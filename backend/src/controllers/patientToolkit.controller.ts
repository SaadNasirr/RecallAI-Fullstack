import { Request, Response } from "express";
import {
  PatientToolkit,
  IMedicationEntry,
  IRoutineEntry,
  IConsentEntry,
  IReminderEntry,
  IAlarmEntry,
} from "../models/PatientToolkit";
import { resolveSubjectUserId } from "../utils/patientScope";

function parseUpdatedAt(raw: unknown): Date | undefined {
  if (typeof raw === "number" && Number.isFinite(raw)) {
    return new Date(raw);
  }
  if (typeof raw === "string") {
    const parsed = Date.parse(raw);
    if (!Number.isNaN(parsed)) return new Date(parsed);
  }
  return undefined;
}

function sanitizeMedication(raw: unknown): IMedicationEntry | null {
  if (!raw || typeof raw !== "object") return null;
  const o = raw as Record<string, unknown>;
  const clientId = String(o.clientId ?? o.id ?? "").trim();
  const name = String(o.name ?? "").trim();
  if (!clientId || !name) return null;
  return {
    clientId,
    name,
    timeLabel: String(o.timeLabel ?? "").trim(),
    notes: String(o.notes ?? "").trim(),
    takenToday: Boolean(o.takenToday),
    takenAt:
      o.takenAt == null || o.takenAt === ""
        ? null
        : Number(o.takenAt) || null,
    snoozeCount: Number(o.snoozeCount) || 0,
    skippedToday: Boolean(o.skippedToday),
    skipReason: String(o.skipReason ?? "").trim(),
    adherenceStatus: String(o.adherenceStatus ?? "PENDING").trim(),
    lastResetDate: String(o.lastResetDate ?? "").trim(),
    updatedAt: parseUpdatedAt(o.updatedAt) ?? new Date(),
  };
}

function sanitizeRoutine(raw: unknown): IRoutineEntry | null {
  if (!raw || typeof raw !== "object") return null;
  const o = raw as Record<string, unknown>;
  const clientId = String(o.clientId ?? o.id ?? "").trim();
  const title = String(o.title ?? "").trim();
  if (!clientId || !title) return null;
  return {
    clientId,
    title,
    period: String(o.period ?? "Morning").trim(),
    frequency: String(o.frequency ?? "Daily").trim(),
    timeLabel: String(o.timeLabel ?? "").trim(),
    doneToday: Boolean(o.doneToday),
    streakDays: Number(o.streakDays) || 0,
    lastCompletedDate: String(o.lastCompletedDate ?? "").trim(),
    updatedAt: parseUpdatedAt(o.updatedAt) ?? new Date(),
  };
}

function sanitizeConsent(raw: unknown): IConsentEntry {
  if (!raw || typeof raw !== "object") {
    return { updatedAt: new Date() };
  }
  const o = raw as Record<string, unknown>;
  return {
    shareWithCaregiver: o.shareWithCaregiver !== false,
    allowLocationSharing: o.allowLocationSharing !== false,
    allowVoiceStorage: o.allowVoiceStorage !== false,
    allowPhotoStorage: o.allowPhotoStorage !== false,
    updatedAt: parseUpdatedAt(o.updatedAt) ?? new Date(),
  };
}

function sanitizeReminder(raw: unknown): IReminderEntry | null {
  if (!raw || typeof raw !== "object") return null;
  const o = raw as Record<string, unknown>;
  const clientId = String(o.clientId ?? o.id ?? "").trim();
  const title = String(o.title ?? "").trim();
  if (!clientId || !title) return null;
  const datetime = Number(o.datetime);
  if (!Number.isFinite(datetime)) return null;
  return {
    clientId,
    title,
    description: String(o.description ?? "").trim(),
    datetime,
    status: String(o.status ?? "PENDING").trim(),
    source: String(o.source ?? "patient").trim(),
    createdAt: Number(o.createdAt) || Date.now(),
    updatedAt: Number(o.updatedAt) || Date.now(),
    warn10Min: o.warn10Min !== false,
    preset: String(o.preset ?? "").trim(),
    repeatMode: String(o.repeatMode ?? "NONE").trim(),
    daysOfWeekMask: Number(o.daysOfWeekMask) || 0,
  };
}

function sanitizeAlarm(raw: unknown): IAlarmEntry | null {
  if (!raw || typeof raw !== "object") return null;
  const o = raw as Record<string, unknown>;
  const clientId = String(o.clientId ?? o.id ?? "").trim();
  const label = String(o.label ?? "").trim();
  if (!clientId || !label) return null;
  const hour = Number(o.hour);
  const minute = Number(o.minute);
  const nextTriggerAt = Number(o.nextTriggerAt);
  if (
    !Number.isFinite(hour) ||
    !Number.isFinite(minute) ||
    !Number.isFinite(nextTriggerAt)
  ) {
    return null;
  }
  return {
    clientId,
    label,
    hour: Math.max(0, Math.min(23, Math.floor(hour))),
    minute: Math.max(0, Math.min(59, Math.floor(minute))),
    repeatMode: String(o.repeatMode ?? "ONCE").trim(),
    daysOfWeekMask: Number(o.daysOfWeekMask) || 0,
    enabled: o.enabled !== false,
    nextTriggerAt,
    createdAt: Number(o.createdAt) || Date.now(),
    updatedAt: Number(o.updatedAt) || Date.now(),
  };
}

export async function getPatientToolkit(req: Request, res: Response) {
  try {
    const userId = await resolveSubjectUserId(req, res, "viewMemories");
    if (!userId) return;

    const doc = await PatientToolkit.findOne({ userId }).lean();
    return res.json({
      medications: doc?.medications ?? [],
      routines: doc?.routines ?? [],
      consent: doc?.consent ?? {},
      reminders: doc?.reminders ?? [],
      alarms: doc?.alarms ?? [],
    });
  } catch (error) {
    console.error("getPatientToolkit error:", error);
    return res.status(500).json({ error: "Failed to load patient toolkit." });
  }
}

export async function putPatientToolkit(req: Request, res: Response) {
  try {
    const userId = await resolveSubjectUserId(req, res, "manageReminders");
    if (!userId) return;

    const rawMeds = req.body?.medications;
    const rawRoutines = req.body?.routines;
    const rawReminders = req.body?.reminders;
    const rawAlarms = req.body?.alarms;
    if (
      !Array.isArray(rawMeds) ||
      !Array.isArray(rawRoutines) ||
      !Array.isArray(rawReminders) ||
      !Array.isArray(rawAlarms)
    ) {
      return res.status(400).json({
        error: "medications, routines, reminders, and alarms arrays are required.",
      });
    }
    if (
      rawMeds.length > 200 ||
      rawRoutines.length > 200 ||
      rawReminders.length > 300 ||
      rawAlarms.length > 100
    ) {
      return res.status(400).json({ error: "Too many schedule entries." });
    }

    const medications: IMedicationEntry[] = [];
    const medSeen = new Set<string>();
    for (const item of rawMeds) {
      const med = sanitizeMedication(item);
      if (!med || medSeen.has(med.clientId)) continue;
      medSeen.add(med.clientId);
      medications.push(med);
    }

    const routines: IRoutineEntry[] = [];
    const routineSeen = new Set<string>();
    for (const item of rawRoutines) {
      const routine = sanitizeRoutine(item);
      if (!routine || routineSeen.has(routine.clientId)) continue;
      routineSeen.add(routine.clientId);
      routines.push(routine);
    }

    const consent = sanitizeConsent(req.body?.consent);

    const reminders: IReminderEntry[] = [];
    const reminderSeen = new Set<string>();
    for (const item of rawReminders) {
      const reminder = sanitizeReminder(item);
      if (!reminder || reminderSeen.has(reminder.clientId)) continue;
      reminderSeen.add(reminder.clientId);
      reminders.push(reminder);
    }

    const alarms: IAlarmEntry[] = [];
    const alarmSeen = new Set<string>();
    for (const item of rawAlarms) {
      const alarm = sanitizeAlarm(item);
      if (!alarm || alarmSeen.has(alarm.clientId)) continue;
      alarmSeen.add(alarm.clientId);
      alarms.push(alarm);
    }

    const doc = await PatientToolkit.findOneAndUpdate(
      { userId },
      { $set: { medications, routines, consent, reminders, alarms } },
      { upsert: true, new: true, setDefaultsOnInsert: true }
    ).lean();

    return res.json({
      medications: doc?.medications ?? medications,
      routines: doc?.routines ?? routines,
      consent: doc?.consent ?? consent,
      reminders: doc?.reminders ?? reminders,
      alarms: doc?.alarms ?? alarms,
    });
  } catch (error) {
    console.error("putPatientToolkit error:", error);
    return res.status(500).json({ error: "Failed to save patient toolkit." });
  }
}
