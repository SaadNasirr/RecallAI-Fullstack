/**
 * End-to-end check: patient generates invite code → caregiver requests → patient approves → lists match.
 *
 * Usage:
 *   node scripts/verify-care-link.mjs
 *   CARE_API_BASE=http://127.0.0.1:3001 node scripts/verify-care-link.mjs
 */

const BASE = (process.env.CARE_API_BASE || "http://127.0.0.1:3001").replace(/\/$/, "");

function rnd() {
  return Math.random().toString(36).slice(2, 12);
}

async function jfetch(path, { method = "GET", token, body } = {}) {
  const headers = { Accept: "application/json" };
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) {
    headers["Content-Type"] = "application/json";
  }
  const r = await fetch(`${BASE}${path}`, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  const text = await r.text();
  let json = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    json = { _raw: text };
  }
  return { ok: r.ok, status: r.status, json };
}

async function main() {
  const { ok: healthOk, json: health } = await jfetch("/health");
  if (!healthOk || health?.status !== "ok") {
    throw new Error(`Backend not healthy at ${BASE}. Got: ${JSON.stringify(health)}`);
  }

  const suffix = rnd();
  const patientEmail = `e2e_pt_${suffix}@local.test`;
  const caregiverEmail = `e2e_cg_${suffix}@local.test`;
  const password = "E2ETest_Pass_99!";

  let r = await jfetch("/auth/register", {
    method: "POST",
    body: {
      name: "E2E Patient",
      email: patientEmail,
      password,
      role: "patient",
      phone: "+15550001001",
      gender: "male",
      liveLat: 40.7128,
      liveLng: -74.006,
    },
  });
  if (!r.ok) throw new Error(`Register patient failed ${r.status}: ${JSON.stringify(r.json)}`);

  r = await jfetch("/auth/login", {
    method: "POST",
    body: { email: patientEmail, password, role: "patient" },
  });
  if (!r.ok) throw new Error(`Login patient failed ${r.status}: ${JSON.stringify(r.json)}`);
  const tokenPatient = r.json.token;
  if (!tokenPatient) throw new Error("No patient token in login response");

  r = await jfetch("/care/invite/generate", {
    method: "POST",
    token: tokenPatient,
    body: { method: "code" },
  });
  if (!r.ok) throw new Error(`invite/generate failed ${r.status}: ${JSON.stringify(r.json)}`);
  const shortCode = r.json.shortCode;
  if (!shortCode) throw new Error("No shortCode in invite response");

  r = await jfetch("/auth/register", {
    method: "POST",
    body: {
      name: "E2E Caregiver",
      email: caregiverEmail,
      password,
      role: "caregiver",
      phone: "+15550002002",
      gender: "female",
    },
  });
  if (!r.ok) throw new Error(`Register caregiver failed ${r.status}: ${JSON.stringify(r.json)}`);

  r = await jfetch("/auth/login", {
    method: "POST",
    body: { email: caregiverEmail, password, role: "caregiver" },
  });
  if (!r.ok) throw new Error(`Login caregiver failed ${r.status}: ${JSON.stringify(r.json)}`);
  const tokenCaregiver = r.json.token;
  if (!tokenCaregiver) throw new Error("No caregiver token");

  r = await jfetch("/care/invite/request", {
    method: "POST",
    token: tokenCaregiver,
    body: { code: shortCode },
  });
  if (!r.ok) throw new Error(`invite/request failed ${r.status}: ${JSON.stringify(r.json)}`);
  const rel = r.json.relationship;
  const relId = rel?._id;
  if (!relId) throw new Error("No relationship id from invite/request");

  r = await jfetch(`/care/request/${relId}/approve`, {
    method: "POST",
    token: tokenPatient,
  });
  if (!r.ok) throw new Error(`approve failed ${r.status}: ${JSON.stringify(r.json)}`);

  r = await jfetch("/care/my-caregivers", { token: tokenPatient });
  if (!r.ok) throw new Error(`my-caregivers failed ${r.status}: ${JSON.stringify(r.json)}`);
  const caregivers = r.json;
  if (!Array.isArray(caregivers) || caregivers.length === 0) {
    throw new Error("Patient sees no caregivers after approve");
  }

  r = await jfetch("/care/my-patients", { token: tokenCaregiver });
  if (!r.ok) throw new Error(`my-patients failed ${r.status}: ${JSON.stringify(r.json)}`);
  const patients = r.json;
  if (!Array.isArray(patients) || patients.length === 0) {
    throw new Error("Caregiver sees no patients after approve");
  }

  console.log("VERIFY_CARE_LINK_OK");
  console.log(
    JSON.stringify(
      {
        base: BASE,
        patientEmail,
        caregiverEmail,
        relationshipId: relId,
        caregiverCount: caregivers.length,
        patientCount: patients.length,
      },
      null,
      2
    )
  );
}

main().catch((e) => {
  console.error("VERIFY_CARE_LINK_FAILED:", e.message || e);
  process.exit(1);
});
