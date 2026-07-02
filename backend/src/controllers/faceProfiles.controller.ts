import { Request, Response } from "express";
import { FaceProfiles, IFaceProfileEntry } from "../models/FaceProfiles";
import { resolveSubjectUserId } from "../utils/patientScope";

const MIN_EMBEDDING_DIM = 64;
const MAX_EMBEDDING_DIM = 512;
const MAX_PROFILES = 100;

function sanitizeProfile(raw: unknown): IFaceProfileEntry | null {
  if (!raw || typeof raw !== "object") return null;
  const o = raw as Record<string, unknown>;
  const clientId = String(o.clientId ?? o.id ?? "").trim();
  const name = String(o.name ?? "").trim();
  if (!clientId || !name) return null;

  const embeddingRaw = o.embedding ?? o.vector;
  if (!Array.isArray(embeddingRaw)) return null;
  const embedding: number[] = [];
  for (const v of embeddingRaw) {
    const n = Number(v);
    if (!Number.isFinite(n)) return null;
    embedding.push(n);
  }
  if (
    embedding.length < MIN_EMBEDDING_DIM ||
    embedding.length > MAX_EMBEDDING_DIM
  ) {
    return null;
  }

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
    embedding,
    updatedAt: updatedAt ?? new Date(),
  };
}

export async function getFaceProfiles(req: Request, res: Response) {
  try {
    const userId = await resolveSubjectUserId(req, res, "viewMemories");
    if (!userId) return;

    const doc = await FaceProfiles.findOne({ userId }).lean();
    return res.json({
      descriptorSchemaVersion: doc?.descriptorSchemaVersion ?? 4,
      profiles: doc?.profiles ?? [],
    });
  } catch (error) {
    console.error("getFaceProfiles error:", error);
    return res.status(500).json({ error: "Failed to load face profiles." });
  }
}

export async function putFaceProfiles(req: Request, res: Response) {
  try {
    const userId = await resolveSubjectUserId(req, res, "manageReminders");
    if (!userId) return;

    const rawList = req.body?.profiles;
    if (!Array.isArray(rawList)) {
      return res.status(400).json({ error: "profiles array is required." });
    }
    if (rawList.length > MAX_PROFILES) {
      return res.status(400).json({ error: `Too many profiles (max ${MAX_PROFILES}).` });
    }

    const schemaVersion = Number(req.body?.descriptorSchemaVersion ?? 4);
    const profiles: IFaceProfileEntry[] = [];
    const seen = new Set<string>();
    for (const item of rawList) {
      const profile = sanitizeProfile(item);
      if (!profile || seen.has(profile.clientId)) continue;
      seen.add(profile.clientId);
      profiles.push(profile);
    }

    const doc = await FaceProfiles.findOneAndUpdate(
      { userId },
      {
        $set: {
          profiles,
          descriptorSchemaVersion: Number.isFinite(schemaVersion)
            ? schemaVersion
            : 4,
        },
      },
      { upsert: true, new: true, setDefaultsOnInsert: true }
    ).lean();

    return res.json({
      descriptorSchemaVersion: doc?.descriptorSchemaVersion ?? schemaVersion,
      profiles: doc?.profiles ?? profiles,
    });
  } catch (error) {
    console.error("putFaceProfiles error:", error);
    return res.status(500).json({ error: "Failed to save face profiles." });
  }
}
