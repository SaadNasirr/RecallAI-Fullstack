import { Request, Response } from "express";
import { PeopleDirectory, IPersonEntry } from "../models/PeopleDirectory";
import { resolveSubjectUserId } from "../utils/patientScope";

function sanitizePerson(raw: unknown): IPersonEntry | null {
  if (!raw || typeof raw !== "object") return null;
  const o = raw as Record<string, unknown>;
  const clientId = String(o.clientId ?? o.id ?? "").trim();
  const name = String(o.name ?? "").trim();
  if (!clientId || !name) return null;
  const updatedAtRaw = o.updatedAt;
  let updatedAt: Date | undefined;
  if (typeof updatedAtRaw === "number" && Number.isFinite(updatedAtRaw)) {
    updatedAt = new Date(updatedAtRaw);
  } else if (typeof updatedAtRaw === "string") {
    const parsed = Date.parse(updatedAtRaw);
    if (!Number.isNaN(parsed)) updatedAt = new Date(parsed);
  }
  return {
    clientId,
    name,
    relation: String(o.relation ?? "").trim(),
    note: String(o.note ?? "").trim(),
    phone: String(o.phone ?? "").trim(),
    updatedAt: updatedAt ?? new Date(),
  };
}

export async function getPeopleDirectory(req: Request, res: Response) {
  try {
    const userId = await resolveSubjectUserId(req, res, "viewMemories");
    if (!userId) return;

    const doc = await PeopleDirectory.findOne({ userId }).lean();
    return res.json({ people: doc?.people ?? [] });
  } catch (error) {
    console.error("getPeopleDirectory error:", error);
    return res.status(500).json({ error: "Failed to load people directory." });
  }
}

export async function putPeopleDirectory(req: Request, res: Response) {
  try {
    const userId = await resolveSubjectUserId(req, res, "manageReminders");
    if (!userId) return;

    const rawList = req.body?.people;
    if (!Array.isArray(rawList)) {
      return res.status(400).json({ error: "people array is required." });
    }
    if (rawList.length > 500) {
      return res.status(400).json({ error: "Too many entries (max 500)." });
    }

    const people: IPersonEntry[] = [];
    const seen = new Set<string>();
    for (const item of rawList) {
      const person = sanitizePerson(item);
      if (!person || seen.has(person.clientId)) continue;
      seen.add(person.clientId);
      people.push(person);
    }

    const doc = await PeopleDirectory.findOneAndUpdate(
      { userId },
      { $set: { people } },
      { upsert: true, new: true, setDefaultsOnInsert: true }
    ).lean();

    return res.json({ people: doc?.people ?? people });
  } catch (error) {
    console.error("putPeopleDirectory error:", error);
    return res.status(500).json({ error: "Failed to save people directory." });
  }
}
