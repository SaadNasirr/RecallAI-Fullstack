const { spawn } = require("child_process");
const { execFileSync } = require("child_process");
const fs = require("fs");
const path = require("path");

/**
 * Cloudflare quick tunnel + backend dev orchestration.
 *
 * If cloudflared loops with "TLS handshake with edge ... forcibly closed":
 * - Set TUNNEL_EDGE_IP_VERSION=auto (default on Windows below).
 * - If cloudflared warns it cannot load the Windows trust store, set TUNNEL_ORIGIN_CA_POOL
 *   to a PEM bundle (e.g. https://curl.se/ca/cacert.pem saved locally). Primarily affects
 *   verifying TLS to your *origin*; edge handshake resets are often firewall/VPN/antivirus.
 * - Try TUNNEL_PROTOCOL=quic if http2 is blocked on your network.
 * - Or DISABLE_TUNNEL=1 and use LAN IP / USB reverse for the phone.
 */
const tunnelUrl = process.env.TUNNEL_URL || "http://127.0.0.1:3001";
const voicePort = process.env.VOICE_SIDECAR_PORT || "8000";
const voiceDir =
  process.env.VOICE_SIDECAR_DIR || "C:\\Users\\Dell\\Desktop\\recallai-voice-sidecar";
// On some Windows networks, HTTP/2 to Cloudflare edge is flaky; QUIC often survives better.
const tunnelProtocol =
  process.env.TUNNEL_PROTOCOL || (process.platform === "win32" ? "quic" : "http2");
/** Prefer "auto" on Windows — strict IPv4 edge routing often breaks behind some ISPs / VPNs. */
const tunnelEdgeIpVersion =
  process.env.TUNNEL_EDGE_IP_VERSION || (process.platform === "win32" ? "auto" : "4");
const tunnelOriginCaPool = process.env.TUNNEL_ORIGIN_CA_POOL || "";
const androidProjectDir =
  process.env.RECALLAI_ANDROID_DIR || "C:\\Users\\Dell\\Desktop\\RecallAI3";

function run(name, command, args, options = {}) {
  const child = spawn(command, args, {
    stdio: "inherit",
    shell: false,
    env: process.env,
    ...options,
  });

  child.on("exit", (code) => {
    if (code !== 0) {
      console.error(`[${name}] exited with code ${code}`);
    }
  });

  child.on("error", (error) => {
    console.error(`[${name}] failed to start: ${error.message}`);
  });

  return child;
}

function runShell(name, commandLine, options = {}) {
  const child = spawn(commandLine, {
    stdio: "inherit",
    shell: true,
    env: process.env,
    ...options,
  });

  child.on("exit", (code) => {
    if (code !== 0) {
      console.error(`[${name}] exited with code ${code}`);
    }
  });

  child.on("error", (error) => {
    console.error(`[${name}] failed to start: ${error.message}`);
  });

  return child;
}

function updateAndroidTunnelBaseUrl(newUrl) {
  const gradlePropsPath = path.join(androidProjectDir, "gradle.properties");
  try {
    const existing = fs.existsSync(gradlePropsPath)
      ? fs.readFileSync(gradlePropsPath, "utf8")
      : "";
    const lines = existing ? existing.split(/\r?\n/) : [];
    const key = "RECALLAI_TUNNEL_BASE_URL";
    const value = `${key}=${newUrl}`;
    const idx = lines.findIndex((line) => line.startsWith(`${key}=`));
    if (idx >= 0) {
      lines[idx] = value;
    } else {
      lines.push(value);
    }
    const next = `${lines.filter((line) => line !== undefined).join("\n").trim()}\n`;
    fs.writeFileSync(gradlePropsPath, next, "utf8");
    console.log(`[tunnel] synced URL to Android gradle.properties: ${newUrl}`);
  } catch (error) {
    console.warn(`[tunnel] failed to sync Android URL: ${error.message}`);
  }
}

function runTunnel() {
  const args = [
    "tunnel",
    "--protocol",
    tunnelProtocol,
    "--edge-ip-version",
    tunnelEdgeIpVersion,
  ];
  if (tunnelOriginCaPool.trim()) {
    args.push("--origin-ca-pool", tunnelOriginCaPool.trim());
  }
  args.push("--url", tunnelUrl);

  const child = spawn(
    "cloudflared",
    args,
    {
      stdio: ["ignore", "pipe", "pipe"],
      shell: false,
      env: process.env,
    }
  );

  const onData = (buf, isErr = false) => {
    const text = buf.toString();
    if (isErr) process.stderr.write(text);
    else process.stdout.write(text);

    const match = text.match(/https:\/\/[a-z0-9-]+\.trycloudflare\.com/gi);
    if (match && match.length > 0) {
      const latest = match[match.length - 1];
      updateAndroidTunnelBaseUrl(latest);
    }
  };

  child.stdout.on("data", (buf) => onData(buf, false));
  child.stderr.on("data", (buf) => onData(buf, true));

  child.on("exit", (code) => {
    if (code !== 0) {
      console.error(`[tunnel] exited with code ${code}`);
    }
  });

  child.on("error", (error) => {
    console.error(`[tunnel] failed to start: ${error.message}`);
  });

  return child;
}

const backend =
  process.platform === "win32"
    ? runShell("backend", "npm run dev")
    : run("backend", "npm", ["run", "dev"]);

let tunnel = null;
let tunnelRetryTimeout = null;
let tunnelCrashWindowStart = Date.now();
let tunnelCrashCount = 0;
function startTunnelWithRetry(delayMs = 0) {
  if (tunnelRetryTimeout) {
    clearTimeout(tunnelRetryTimeout);
    tunnelRetryTimeout = null;
  }
  const launch = () => {
    const caHint = tunnelOriginCaPool.trim()
      ? `, origin-ca-pool=${tunnelOriginCaPool.trim()}`
      : "";
    console.log(
      `[tunnel] starting cloudflared (protocol=${tunnelProtocol}, edge-ip-version=${tunnelEdgeIpVersion}${caHint})`
    );
    tunnel = runTunnel();
    tunnel.once("exit", (code) => {
      if (isShuttingDown) return;
      const now = Date.now();
      if (now - tunnelCrashWindowStart > 60_000) {
        tunnelCrashWindowStart = now;
        tunnelCrashCount = 0;
      }
      tunnelCrashCount += 1;
      // If cloudflared is repeatedly failing (e.g., DNS local resolver timeouts), keep backend alive
      // and stop trying to bring the tunnel back until the next manual restart.
      if (tunnelCrashCount >= 6) {
        console.warn(
          `[tunnel] cloudflared exited too many times in 60s (count=${tunnelCrashCount}). Disabling tunnel retries for this run.`
        );
        return;
      }
      const retryInMs = 5000;
      console.warn(`[tunnel] exited with code ${code}. Retrying in ${retryInMs / 1000}s...`);
      tunnelRetryTimeout = setTimeout(() => startTunnelWithRetry(), retryInMs);
    });
  };

  if (delayMs > 0) {
    tunnelRetryTimeout = setTimeout(launch, delayMs);
  } else {
    launch();
  }
}
const disableTunnel =
  process.env.DISABLE_TUNNEL === "1" ||
  String(process.env.DISABLE_TUNNEL || "").toLowerCase() === "true";
if (disableTunnel) {
  console.warn(
    "[tunnel] DISABLE_TUNNEL is set — skipping cloudflared. Point the app at your LAN IP or use adb reverse; tunnel URL will not be synced."
  );
} else {
  startTunnelWithRetry();
}

let voice = null;
const venvPython = path.join(voiceDir, ".venv", "Scripts", "python.exe");
if (fs.existsSync(voiceDir)) {
  try {
    execFileSync(process.execPath, [path.join(__dirname, "killPort.js"), String(voicePort)], {
      stdio: "ignore",
    });
  } catch {
    // Ignore if port cleanup fails; startup will report concrete bind errors.
  }
  if (fs.existsSync(venvPython)) {
    voice = run("voice", venvPython, [
      "-m",
      "uvicorn",
      "main:app",
      "--host",
      "127.0.0.1",
      "--port",
      String(voicePort),
    ], { cwd: voiceDir });
  } else {
    const pyCmd = process.platform === "win32" ? "py.exe" : "python3";
    voice = run("voice", pyCmd, [
      "-m",
      "uvicorn",
      "main:app",
      "--host",
      "127.0.0.1",
      "--port",
      String(voicePort),
    ], { cwd: voiceDir });
  }
} else {
  console.warn(`[voice] sidecar directory not found: ${voiceDir}`);
}

function shutdown(signal) {
  isShuttingDown = true;
  backend.kill(signal);
  if (tunnel) {
    tunnel.kill(signal);
  }
  if (voice) {
    voice.kill(signal);
  }
  if (tunnelRetryTimeout) {
    clearTimeout(tunnelRetryTimeout);
  }
  process.exit(0);
}

let isShuttingDown = false;
process.on("SIGINT", () => shutdown("SIGINT"));
process.on("SIGTERM", () => shutdown("SIGTERM"));

