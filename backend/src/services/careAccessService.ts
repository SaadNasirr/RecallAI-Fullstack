import { Types } from "mongoose";
import {
  CareRelationship,
  ICareRelationship,
  CarePermissions,
} from "../models/CareRelationship";

export async function findApprovedRelationship(
  caregiverId: string,
  patientId: string
): Promise<ICareRelationship | null> {
  if (
    !Types.ObjectId.isValid(caregiverId) ||
    !Types.ObjectId.isValid(patientId)
  ) {
    return null;
  }
  return CareRelationship.findOne({
    caregiverId: new Types.ObjectId(caregiverId),
    patientId: new Types.ObjectId(patientId),
    status: "approved",
  }).exec();
}

export async function assertApprovedCaregiverPatient(
  caregiverId: string,
  patientId: string
): Promise<ICareRelationship> {
  const rel = await findApprovedRelationship(caregiverId, patientId);
  if (!rel) {
    const err = new Error("FORBIDDEN") as Error & { status?: number };
    err.status = 403;
    throw err;
  }
  return rel;
}

export async function canAccessMemories(
  caregiverId: string,
  patientId: string
): Promise<boolean> {
  const rel = await findApprovedRelationship(caregiverId, patientId);
  return Boolean(rel?.permissions?.viewMemories);
}

export async function canManageReminders(
  caregiverId: string,
  patientId: string
): Promise<boolean> {
  const rel = await findApprovedRelationship(caregiverId, patientId);
  return Boolean(rel?.permissions?.manageReminders);
}

export async function canViewLocation(
  caregiverId: string,
  patientId: string
): Promise<boolean> {
  const rel = await findApprovedRelationship(caregiverId, patientId);
  return Boolean(rel?.permissions?.viewLocation);
}

export async function canEmergencyAccess(
  caregiverId: string,
  patientId: string
): Promise<boolean> {
  const rel = await findApprovedRelationship(caregiverId, patientId);
  return Boolean(rel?.permissions?.emergencyAccess);
}

export async function receivesAlerts(
  caregiverId: string,
  patientId: string
): Promise<boolean> {
  const rel = await findApprovedRelationship(caregiverId, patientId);
  return Boolean(rel?.permissions?.receiveAlerts);
}

export function mergePermissions(
  base: CarePermissions,
  patch: Partial<CarePermissions>
): CarePermissions {
  return {
    viewMemories:
      patch.viewMemories !== undefined ? patch.viewMemories : base.viewMemories,
    manageReminders:
      patch.manageReminders !== undefined
        ? patch.manageReminders
        : base.manageReminders,
    receiveAlerts:
      patch.receiveAlerts !== undefined
        ? patch.receiveAlerts
        : base.receiveAlerts,
    viewLocation:
      patch.viewLocation !== undefined ? patch.viewLocation : base.viewLocation,
    emergencyAccess:
      patch.emergencyAccess !== undefined
        ? patch.emergencyAccess
        : base.emergencyAccess,
  };
}

export async function listApprovedPatientIdsForCaregiver(
  caregiverId: string
): Promise<Types.ObjectId[]> {
  const rows = await CareRelationship.find({
    caregiverId: new Types.ObjectId(caregiverId),
    status: "approved",
  })
    .select("patientId")
    .lean();
  return rows.map((r) => r.patientId as Types.ObjectId);
}
