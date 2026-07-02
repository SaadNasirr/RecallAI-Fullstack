// Windows-friendly: free a TCP port before starting dev server.
// Usage: node scripts/killPort.js 3001
const { execSync } = require("child_process");

const port = Number(process.argv[2] || 3001);
if (!Number.isFinite(port) || port <= 0) {
  process.exit(0);
}

function safeExec(cmd) {
  try {
    return execSync(cmd, { stdio: ["ignore", "pipe", "pipe"] }).toString();
  } catch {
    return "";
  }
}

// netstat output contains lines like:
// TCP    0.0.0.0:3001   ...   LISTENING   14636
const out = safeExec(`netstat -ano | findstr :${port}`);
if (!out.trim()) process.exit(0);

const pids = new Set();
out.split(/\r?\n/).forEach((line) => {
  const parts = line.trim().split(/\s+/);
  const pid = parts[parts.length - 1];
  if (pid && /^\d+$/.test(pid)) pids.add(pid);
});

for (const pid of pids) {
  // Don't kill our own process.
  if (pid === String(process.pid)) continue;
  safeExec(`taskkill /PID ${pid} /F`);
}

