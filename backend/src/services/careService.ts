import crypto from "crypto";
import { Types } from "mongoose";
import { CareInvite } from "../models/CareInvite";
import {
  CareRelationship,
  ICareRelationship,
  CarePermissions,
} from "../models/CareRelationship";
import { CareAlert } from "../models/CareAlert";
import { Geofence } from "../models/Geofence";
import { GeofenceEvent } from "../models/GeofenceEvent";
import { User } from "../models/User";
import { CareTask } from "../models/CareTask";
import * as careAccess from "./careAccessService";
import { EmergencyLog } from "../models/EmergencyLog";
import { sendPushToCaregivers } from "./fcmService";

const QR_EXPIRY_MS = 20 * 60 * 1000;
const CODE_EXPIRY_MS = 30 * 60 * 1000;
const CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

function hashToken(raw: string): string {
  return crypto.createHash("sha256").update(raw).digest("hex");
}

function randomBytesUrlSafe(n: number): string {
  return crypto.randomBytes(n).toString("base64url");
}

function generateShortCode(len = 8): string {
  let out = "";
  for (let i = 0; i < len; i++) {
    const idx = crypto.randomInt(0, CODE_ALPHABET.length);
    out += CODE_ALPHABET[idx];
  }
  return out;
}

const defaultPermissions = (): CarePermissions => ({
  viewMemories: true,
  manageReminders: true,
  receiveAlerts: true,
  viewLocation: true,
  emergencyAccess: true,
});

async function ensureRelationshipSlot(
  patientId: Types.ObjectId,
  caregiverId: Types.ObjectId,
  inviteMethod: "qr" | "code"
): Promise<ICareRelationship> {
  const existing = await CareRelationship.findOne({
    patientId,
    caregiverId,
  });
  if (existing) {
    if (existing.status === "blocked") {
      const err = new Error("BLOCKED") as Error & { status?: number };
      err.status = 403;
      throw err;
    }
    // Already linked: treat as success so invite/code flows are idempotent (no HTTP 409).
    if (existing.status === "approved") {
      return existing;
    }
    existing.status = "pending";
    existing.inviteMethod = inviteMethod;
    await existing.save();
    return existing;
  }
  const doc = new CareRelationship({
    patientId,
    caregiverId,
    status: "pending",
    permissions: defaultPermissions(),
    relationshipType: "secondary",
    inviteMethod,
  });
  await doc.save();
  return doc;
}

export async function createInviteQr(patientId: string): Promise<{
  token: string;
  expiresAt: Date;
}> {
  const raw = randomBytesUrlSafe(32);
  const tokenHash = hashToken(raw);
  const expiresAt = new Date(Date.now() + QR_EXPIRY_MS);
  await CareInvite.create({
    patientId: new Types.ObjectId(patientId),
    tokenHash,
    expiresAt,
    method: "qr",
  });
  return { token: raw, expiresAt };
}

export async function createInviteCode(patientId: string): Promise<{
  shortCode: string;
  expiresAt: Date;
}> {
  let shortCode = generateShortCode(8);
  let attempts = 0;
  const expiresAt = new Date(Date.now() + CODE_EXPIRY_MS);
  while (attempts < 8) {
    try {
      const raw = shortCode;
      const tokenHash = hashToken(raw);
      await CareInvite.create({
        patientId: new Types.ObjectId(patientId),
        tokenHash,
        shortCode,
        expiresAt,
        method: "code",
      });
      return { shortCode, expiresAt };
    } catch {
      shortCode = generateShortCode(8);
      attempts++;
    }
  }
  throw new Error("Could not allocate invite code");
}

async function consumeInvite(
  tokenHash: string
): Promise<{ patientId: Types.ObjectId; method: "qr" | "code" }> {
  const invite = await CareInvite.findOne({ tokenHash }).exec();
  if (!invite || invite.consumedAt) {
    const err = new Error("INVALID_INVITE") as Error & { status?: number };
    err.status = 400;
    throw err;
  }
  if (invite.expiresAt.getTime() < Date.now()) {
    const err = new Error("EXPIRED_INVITE") as Error & { status?: number };
    err.status = 400;
    throw err;
  }
  invite.consumedAt = new Date();
  await invite.save();
  return {
    patientId: invite.patientId as Types.ObjectId,
    method: invite.method as "qr" | "code",
  };
}

export async function caregiverRequestFromQrToken(
  caregiverId: string,
  rawToken: string
): Promise<ICareRelationship> {
  const caregiver = await User.findById(caregiverId);
  if (!caregiver || caregiver.role !== "caregiver") {
    const err = new Error("INVALID_ROLE") as Error & { status?: number };
    err.status = 403;
    throw err;
  }
  const tokenHash = hashToken(rawToken.trim());
  const { patientId, method } = await consumeInvite(tokenHash);
  if (patientId.equals(new Types.ObjectId(caregiverId))) {
    const err = new Error("SELF") as Error & { status?: number };
    err.status = 400;
    throw err;
  }
  return ensureRelationshipSlot(patientId, new Types.ObjectId(caregiverId), method);
}

export async function caregiverRequestFromCode(
  caregiverId: string,
  code: string
): Promise<ICareRelationship> {
  const caregiver = await User.findById(caregiverId);
  if (!caregiver || caregiver.role !== "caregiver") {
    const err = new Error("INVALID_ROLE") as Error & { status?: number };
    err.status = 403;
    throw err;
  }
  const normalized = code.trim().toUpperCase();
  const tokenHash = hashToken(normalized);
  const { patientId, method } = await consumeInvite(tokenHash);
  if (patientId.equals(new Types.ObjectId(caregiverId))) {
    const err = new Error("SELF") as Error & { status?: number };
    err.status = 400;
    throw err;
  }
  return ensureRelationshipSlot(patientId, new Types.ObjectId(caregiverId), method);
}

export async function approveRequest(
  patientId: string,
  relationshipId: string
): Promise<ICareRelationship> {
  const rel = await CareRelationship.findById(relationshipId);
  if (!rel || !rel.patientId.equals(new Types.ObjectId(patientId))) {
    const err = new Error("NOT_FOUND") as Error & { status?: number };
    err.status = 404;
    throw err;
  }
  if (rel.status !== "pending") {
    const err = new Error("INVALID_STATE") as Error & { status?: number };
    err.status = 400;
    throw err;
  }
  const primaryCount = await CareRelationship.countDocuments({
    patientId: rel.patientId,
    status: "approved",
    relationshipType: "primary",
  });
  rel.status = "approved";
  if (primaryCount === 0) {
    rel.relationshipType = "primary";
  } else {
    rel.relationshipType = "secondary";
  }
  await rel.save();
  return rel;
}

export async function rejectRequest(
  patientId: string,
  relationshipId: string
): Promise<ICareRelationship> {
  const rel = await CareRelationship.findById(relationshipId);
  if (!rel || !rel.patientId.equals(new Types.ObjectId(patientId))) {
    const err = new Error("NOT_FOUND") as Error & { status?: number };
    err.status = 404;
    throw err;
  }
  if (rel.status !== "pending") {
    const err = new Error("INVALID_STATE") as Error & { status?: number };
    err.status = 400;
    throw err;
  }
  rel.status = "rejected";
  await rel.save();
  return rel;
}

export async function listCaregiversForPatient(patientId: string) {
  const rows = await CareRelationship.find({
    patientId: new Types.ObjectId(patientId),
    status: "approved",
  })
    .populate("patientId", "name email role phone liveLat liveLng liveLocationUpdatedAt gender")
    .populate("caregiverId", "name email role phone")
    .lean();
  return rows;
}

export async function listPatientsForCaregiver(caregiverId: string) {
  const rows = await CareRelationship.find({
    caregiverId: new Types.ObjectId(caregiverId),
    status: "approved",
  })
    .populate(
      "patientId",
      "name email role phone liveLat liveLng liveLocationUpdatedAt"
    )
    .lean();
  return rows;
}

export async function updatePatientLiveLocation(
  patientId: string,
  lat: number,
  lng: number
): Promise<void> {
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) {
    const err = new Error("INVALID_COORDS") as Error & { status?: number };
    err.status = 400;
    throw err;
  }
  const user = await User.findById(patientId);
  if (!user || user.role !== "patient") {
    const err = new Error("NOT_FOUND") as Error & { status?: number };
    err.status = 404;
    throw err;
  }
  user.liveLat = lat;
  user.liveLng = lng;
  user.liveLocationUpdatedAt = new Date();
  await user.save();
}

export async function listPendingForPatient(patientId: string) {
  return CareRelationship.find({
    patientId: new Types.ObjectId(patientId),
    status: "pending",
  })
    .populate("patientId", "name email role phone liveLat liveLng liveLocationUpdatedAt gender")
    .populate("caregiverId", "name email role phone")
    .lean();
}

export async function patchRelationshipPermissions(
  patientId: string,
  relationshipId: string,
  patch: Partial<CarePermissions>
): Promise<ICareRelationship> {
  const rel = await CareRelationship.findById(relationshipId);
  if (!rel || !rel.patientId.equals(new Types.ObjectId(patientId))) {
    const err = new Error("NOT_FOUND") as Error & { status?: number };
    err.status = 404;
    throw err;
  }
  if (rel.status !== "approved") {
    const err = new Error("INVALID_STATE") as Error & { status?: number };
    err.status = 400;
    throw err;
  }
  rel.permissions = careAccess.mergePermissions(rel.permissions, patch);
  await rel.save();
  return rel;
}

export async function notifyEmergency(patientId: string, metadata?: Record<string, unknown>) {
  let rels = await CareRelationship.find({
    patientId: new Types.ObjectId(patientId),
    status: "approved",
    "permissions.emergencyAccess": true,
  })
    .populate("caregiverId", "name email phone")
    .lean();

  // If nobody opted into emergencyAccess, still notify all approved caregivers (better than silent no-op).
  if (!rels.length) {
    rels = await CareRelationship.find({
      patientId: new Types.ObjectId(patientId),
      status: "approved",
    })
      .populate("caregiverId", "name email phone")
      .lean();
  }

  const ordered = [...rels].sort((a, b) => {
    if (a.relationshipType === "primary" && b.relationshipType !== "primary")
      return -1;
    if (b.relationshipType === "primary" && a.relationshipType !== "primary")
      return 1;
    return (
      new Date((a as any).createdAt).getTime() -
      new Date((b as any).createdAt).getTime()
    );
  });

  const caregiverIds = ordered.map((r) => r.caregiverId as Types.ObjectId);
  const msg =
    metadata && typeof metadata.message === "string" ? metadata.message.trim() : "";
  const et =
    metadata && typeof metadata.type === "string" && metadata.type.trim()
      ? String(metadata.type).trim()
      : "sos";
  const lat = typeof metadata?.lat === "number" ? (metadata.lat as number) : undefined;
  const lng = typeof metadata?.lng === "number" ? (metadata.lng as number) : undefined;

  await EmergencyLog.create({
    patientId: new Types.ObjectId(patientId),
    type: et,
    message: msg || undefined,
    lat,
    lng,
    metadata: metadata || {},
  });

  const pat = await User.findById(patientId).select("name").lean();
  const pname = String((pat as { name?: string })?.name || "").trim() || "Patient";
  const title = "Emergency alert";
  const body =
    msg ||
    `The patient triggered an emergency alert.${lat != null && lng != null ? ` Location: ${lat},${lng}` : ""}`;
  const alert = await CareAlert.create({
    patientId: new Types.ObjectId(patientId),
    caregiverIds,
    type: "emergency",
    title,
    body,
    metadata: {
      ...(metadata || {}),
      ts: new Date().toISOString(),
      emergencyType: et,
    },
  });
  await sendPushToCaregivers(
    caregiverIds,
    { title: `Emergency: ${pname}`, body },
    {
      type: "emergency",
      patientId,
      alertId: String(alert._id),
      screen: "alert_center",
    },
    "high"
  );
  return { alert, caregiverIds };
}

/**
 * Shape relationships for API/Mobile clients: Moshi expects `patientId`/`caregiverId`
 * as populated User objects, not raw ObjectId strings.
 */
export async function relationshipForApi(relId: string) {
  const doc = await CareRelationship.findById(relId)
    .populate("patientId", "name email role phone liveLat liveLng liveLocationUpdatedAt gender")
    .populate("caregiverId", "name email role phone gender")
    .lean();
  if (!doc) {
    const err = new Error("NOT_FOUND") as Error & { status?: number };
    err.status = 404;
    throw err;
  }
  return doc;
}

export async function dispatchPatientAlert(
  patientId: string,
  type: string,
  title: string,
  body: string,
  metadata?: Record<string, unknown>
) {
  let rels = await CareRelationship.find({
    patientId: new Types.ObjectId(patientId),
    status: "approved",
    "permissions.receiveAlerts": true,
  }).lean();

  if (!rels.length) {
    rels = await CareRelationship.find({
      patientId: new Types.ObjectId(patientId),
      status: "approved",
    }).lean();
  }

  const ordered = [...rels].sort((a, b) => {
    if (a.relationshipType === "primary" && b.relationshipType !== "primary")
      return -1;
    if (b.relationshipType === "primary" && a.relationshipType !== "primary")
      return 1;
    return (
      new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()
    );
  });

  const caregiverIds = ordered.map((r) => r.caregiverId as Types.ObjectId);
  const alert = await CareAlert.create({
    patientId: new Types.ObjectId(patientId),
    caregiverIds,
    type,
    title,
    body,
    metadata: metadata || {},
  });
  const pr: "high" | "normal" =
    type === "emergency" ||
    type === "missed_task" ||
    type === "location_share" ||
    type === "geofence_exit" ||
    type === "geofence_enter"
      ? "high"
      : "normal";
  await sendPushToCaregivers(
    caregiverIds,
    { title, body },
    {
      type,
      patientId,
      alertId: String(alert._id),
      screen: "alert_center",
    },
    pr
  );
  return { alert, caregiverIds };
}

export async function ensureMissedTaskAlerts(patientId: string): Promise<void> {
  const now = new Date();
  const pending = await CareTask.find({
    patientId: new Types.ObjectId(patientId),
    status: "pending",
    dueAt: { $lt: now },
  }).lean();
  for (const t of pending) {
    const tid = String(t._id);
    const exists = await CareAlert.findOne({
      type: "missed_task",
      patientId: new Types.ObjectId(patientId),
      "metadata.taskId": tid,
    }).lean();
    if (exists) continue;
    await dispatchPatientAlert(
      patientId,
      "missed_task",
      "Missed care task",
      `Task not completed by due time: ${t.title}`,
      { taskId: tid, dueAt: t.dueAt?.toISOString() }
    );
  }
}

export async function patientShareLocation(
  patientId: string,
  address: string,
  lat?: number,
  lng?: number
) {
  const pat = await User.findById(patientId).select("name").lean();
  const pname = String((pat as { name?: string })?.name || "").trim() || "Patient";
  const when = new Date().toLocaleString("en-US");
  const body = `${pname} is at ${address} — ${when}`;
  return dispatchPatientAlert(patientId, "location_share", "Location share", body, {
    address,
    lat,
    lng,
  });
}

export async function getWatchlistSnapshot(caregiverId: string) {
  const rows = await listPatientsForCaregiver(caregiverId);
  const start = new Date();
  start.setHours(0, 0, 0, 0);
  const cgOid = new Types.ObjectId(caregiverId);
  const out: any[] = [];
  for (const rel of rows) {
    const p = rel.patientId as any;
    if (!p?._id) continue;
    const pid = p._id.toString();
    await ensureMissedTaskAlerts(pid).catch(() => {});
    const pendingTasks = await CareTask.countDocuments({
      patientId: p._id,
      status: "pending",
    });
    const doneToday = await CareTask.countDocuments({
      patientId: p._id,
      status: "done",
      doneAt: { $gte: start },
    });
    const totalToday = await CareTask.countDocuments({
      patientId: p._id,
      createdAt: { $gte: start },
    });
    const unreadEmergencies = await CareAlert.countDocuments({
      patientId: p._id,
      type: "emergency",
      caregiverIds: cgOid,
      $nor: [{ readBy: { $elemMatch: { caregiverId: cgOid } } }],
    });
    out.push({
      relationship: rel,
      pendingTasks,
      doneToday,
      totalToday,
      unreadEmergencies,
    });
  }
  return out;
}

export async function markAlertReadForCaregiver(
  caregiverId: string,
  alertId: string
): Promise<void> {
  if (!Types.ObjectId.isValid(alertId)) {
    const err = new Error("NOT_FOUND") as Error & { status?: number };
    err.status = 404;
    throw err;
  }
  const cg = new Types.ObjectId(caregiverId);
  const alert = await CareAlert.findOne({
    _id: new Types.ObjectId(alertId),
    caregiverIds: cg,
  });
  if (!alert) {
    const err = new Error("NOT_FOUND") as Error & { status?: number };
    err.status = 404;
    throw err;
  }
  const reads = alert.readBy || [];
  const filtered = reads.filter((r: any) => !r.caregiverId?.equals(cg));
  filtered.push({ caregiverId: cg, readAt: new Date() } as any);
  alert.readBy = filtered as any;
  await alert.save();
}

export async function markAllAlertsReadForCaregiver(caregiverId: string): Promise<void> {
  const cg = new Types.ObjectId(caregiverId);
  const alerts = await CareAlert.find({ caregiverIds: cg }).exec();
  const now = new Date();
  for (const alert of alerts) {
    const reads = alert.readBy || [];
    if (reads.some((r: any) => r.caregiverId?.equals(cg))) continue;
    reads.push({ caregiverId: cg, readAt: now } as any);
    alert.readBy = reads as any;
    await alert.save();
  }
}

export async function countUnreadAlertsForCaregiver(caregiverId: string): Promise<number> {
  const cg = new Types.ObjectId(caregiverId);
  return CareAlert.countDocuments({
    caregiverIds: cg,
    $nor: [{ readBy: { $elemMatch: { caregiverId: cg } } }],
  });
}

/** Patient or caregiver may unlink; removes geofence data for that pair. */
export async function removeRelationship(
  actorUserId: string,
  relationshipId: string
): Promise<void> {
  if (!Types.ObjectId.isValid(relationshipId)) {
    const err = new Error("NOT_FOUND") as Error & { status?: number };
    err.status = 404;
    throw err;
  }
  const rel = await CareRelationship.findById(relationshipId);
  if (!rel) {
    const err = new Error("NOT_FOUND") as Error & { status?: number };
    err.status = 404;
    throw err;
  }
  const actor = new Types.ObjectId(actorUserId);
  const allowed =
    rel.patientId.equals(actor) || rel.caregiverId.equals(actor);
  if (!allowed) {
    const err = new Error("FORBIDDEN") as Error & { status?: number };
    err.status = 403;
    throw err;
  }
  const zoneIds = await Geofence.find({
    patientId: rel.patientId,
    caregiverId: rel.caregiverId,
  })
    .select("_id")
    .lean();
  const ids = zoneIds.map((z) => z._id);
  if (ids.length > 0) {
    await GeofenceEvent.deleteMany({ geofenceId: { $in: ids } });
  }
  await Geofence.deleteMany({
    patientId: rel.patientId,
    caregiverId: rel.caregiverId,
  });
  await rel.deleteOne();
}
