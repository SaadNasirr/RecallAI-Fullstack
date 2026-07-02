import { Request, Response } from "express";
import { Types } from "mongoose";
import type { CarePermissions } from "../models/CareRelationship";
import { findApprovedRelationship } from "../services/careAccessService";

type PermKey = keyof CarePermissions;

export async function resolveSubjectUserId(
  req: Request,
  res: Response,
  permission: PermKey
): Promise<Types.ObjectId | null> {
  const role = req.user.role as string;
  const selfId = new Types.ObjectId(req.user.id);

  if (role === "patient") {
    return selfId;
  }

  const raw =
    (req.query.patientId as string | undefined) ||
    (typeof req.body?.patientId === "string" ? req.body.patientId : undefined);
  if (!raw || !Types.ObjectId.isValid(raw)) {
    res.status(400).json({ message: "patientId required" });
    return null;
  }

  const rel = await findApprovedRelationship(req.user.id, raw);
  if (!rel || !rel.permissions?.[permission]) {
    res.status(403).json({ message: "Forbidden" });
    return null;
  }

  return new Types.ObjectId(raw);
}
