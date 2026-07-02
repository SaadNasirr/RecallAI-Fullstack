import { Request, Response } from "express";
import { Types } from "mongoose";
import { CareRelationship } from "../models/CareRelationship";
import { Geofence } from "../models/Geofence";
import { GeofenceEvent } from "../models/GeofenceEvent";
import { dispatchPatientAlert } from "../services/careService";
import { sendAlertToLinkedCaregivers } from "../services/notificationService";

const MIN_R = 50;
const MAX_R = 5000;

async function assertCaregiverLinkedToPatient(
  caregiverId: string,
  patientId: string
) {
  const rel = await CareRelationship.findOne({
    caregiverId: new Types.ObjectId(caregiverId),
    patientId: new Types.ObjectId(patientId),
    status: "approved",
  }).lean();
  if (!rel) {
    const err = new Error("FORBIDDEN") as Error & { status?: number };
    err.status = 403;
    throw err;
  }
}

export const postCreateGeofence = async (req: Request, res: Response) => {
  try {
    const caregiverId = req.user.id as string;
    const body = req.body as {
      patientId?: string;
      name?: string;
      centerLat?: number;
      centerLng?: number;
      radiusMeters?: number;
      color?: string;
    };
    const patientId = body.patientId?.trim();
    if (!patientId) return res.status(400).json({ message: "patientId required" });
    const name = body.name?.trim();
    if (!name) return res.status(400).json({ message: "name required" });
    if (
      typeof body.centerLat !== "number" ||
      typeof body.centerLng !== "number" ||
      typeof body.radiusMeters !== "number"
    ) {
      return res.status(400).json({ message: "centerLat, centerLng, radiusMeters required" });
    }
    const r = Math.round(body.radiusMeters);
    if (r < MIN_R || r > MAX_R) {
      return res.status(400).json({ message: `radius must be between ${MIN_R} and ${MAX_R}` });
    }
    await assertCaregiverLinkedToPatient(caregiverId, patientId);
    const color = (body.color && body.color.trim()) || "#1E88E5";
    const doc = await Geofence.create({
      patientId: new Types.ObjectId(patientId),
      caregiverId: new Types.ObjectId(caregiverId),
      name,
      centerLat: body.centerLat,
      centerLng: body.centerLng,
      radiusMeters: r,
      color,
      isActive: true,
    });
    return res.status(201).json(formatGeofence(doc));
  } catch (e) {
    return handle(e, res);
  }
};

export const getMyZones = async (req: Request, res: Response) => {
  try {
    const role = req.user.role as string;
    const userId = req.user.id as string;
    const qPatient = (req.query.patientId as string | undefined)?.trim();
    if (role === "patient") {
      const rows = await Geofence.find({
        patientId: new Types.ObjectId(userId),
      })
        .sort({ updatedAt: -1 })
        .lean();
      return res.json(rows.map(formatGeofenceLean));
    }
    if (role !== "caregiver")
      return res.status(403).json({ message: "Forbidden" });
    const patientId = qPatient;
    if (!patientId) return res.status(400).json({ message: "patientId query required" });
    await assertCaregiverLinkedToPatient(userId, patientId);
    const rows = await Geofence.find({
      patientId: new Types.ObjectId(patientId),
    })
      .sort({ updatedAt: -1 })
      .lean();
    return res.json(rows.map(formatGeofenceLean));
  } catch (e) {
    return handle(e, res);
  }
};

export const deleteGeofence = async (req: Request, res: Response) => {
  try {
    const caregiverId = req.user.id as string;
    const { id } = req.params;
    if (!Types.ObjectId.isValid(id))
      return res.status(400).json({ message: "invalid id" });
    const zone = await Geofence.findById(id);
    if (!zone) return res.status(404).json({ message: "not found" });
    if (!zone.caregiverId.equals(new Types.ObjectId(caregiverId))) {
      return res.status(403).json({ message: "Forbidden" });
    }
    await assertCaregiverLinkedToPatient(caregiverId, zone.patientId.toString());
    await GeofenceEvent.deleteMany({ geofenceId: zone._id });
    await zone.deleteOne();
    return res.status(204).send();
  } catch (e) {
    return handle(e, res);
  }
};

export const patchToggleGeofence = async (req: Request, res: Response) => {
  try {
    const caregiverId = req.user.id as string;
    const { id } = req.params;
    if (!Types.ObjectId.isValid(id))
      return res.status(400).json({ message: "invalid id" });
    const zone = await Geofence.findById(id);
    if (!zone) return res.status(404).json({ message: "not found" });
    if (!zone.caregiverId.equals(new Types.ObjectId(caregiverId))) {
      return res.status(403).json({ message: "Forbidden" });
    }
    await assertCaregiverLinkedToPatient(caregiverId, zone.patientId.toString());
    zone.isActive = !zone.isActive;
    await zone.save();
    return res.json(formatGeofence(zone));
  } catch (e) {
    return handle(e, res);
  }
};

export const postGeofenceEvent = async (req: Request, res: Response) => {
  try {
    const patientId = req.user.id as string;
    const role = req.user.role as string;
    if (role !== "patient")
      return res.status(403).json({ message: "patient role required" });
    const body = req.body as {
      geofenceId?: string;
      eventType?: "entered" | "exited";
      lat?: number;
      lng?: number;
    };
    const gfId = body.geofenceId?.trim();
    if (!gfId || !Types.ObjectId.isValid(gfId))
      return res.status(400).json({ message: "geofenceId required" });
    if (body.eventType !== "entered" && body.eventType !== "exited")
      return res.status(400).json({ message: "eventType must be entered or exited" });
    if (typeof body.lat !== "number" || typeof body.lng !== "number")
      return res.status(400).json({ message: "lat and lng required" });
    const zone = await Geofence.findById(gfId);
    if (!zone || !zone.patientId.equals(new Types.ObjectId(patientId))) {
      return res.status(404).json({ message: "geofence not found" });
    }
    const ev = await GeofenceEvent.create({
      geofenceId: zone._id,
      patientId: new Types.ObjectId(patientId),
      eventType: body.eventType,
      triggeredAt: new Date(),
      location: { lat: body.lat, lng: body.lng },
    });
    const patientName = String((req.user as any).name || "").trim() || "Patient";
    if (body.eventType === "entered") {
      await dispatchPatientAlert(
        patientId,
        "geofence_enter",
        "Entered safe zone",
        `${patientName} entered ${zone.name}.`,
        {
          geofenceId: gfId,
          geofenceName: zone.name,
          eventType: "entered",
          eventId: (ev._id as Types.ObjectId).toString(),
          source: "geofence",
        }
      );
    } else if (body.eventType === "exited") {
      await sendAlertToLinkedCaregivers(
        patientId,
        "Left safe zone",
        `${patientName} exited ${zone.name}.`,
        {
          geofenceId: gfId,
          geofenceName: zone.name,
          eventType: "exited",
          eventId: (ev._id as Types.ObjectId).toString(),
        }
      );
    }
    return res.status(204).send();
  } catch (e) {
    return handle(e, res);
  }
};

export const getGeofenceEvents = async (req: Request, res: Response) => {
  try {
    const caregiverId = req.user.id as string;
    const { patientId } = req.params;
    if (!Types.ObjectId.isValid(patientId))
      return res.status(400).json({ message: "invalid patientId" });
    await assertCaregiverLinkedToPatient(caregiverId, patientId);
    const events = await GeofenceEvent.find({
      patientId: new Types.ObjectId(patientId),
    })
      .sort({ triggeredAt: -1 })
      .limit(500)
      .populate("geofenceId", "name")
      .lean();
    const out = events.map((e: any) => {
      let zoneName = "Zone";
      const g = e.geofenceId;
      if (g && typeof g === "object" && "name" in g) {
        zoneName = String((g as { name?: string }).name || "Zone");
      }
      return {
        _id: e._id.toString(),
        geofenceId: e.geofenceId?._id
          ? e.geofenceId._id.toString()
          : e.geofenceId.toString(),
        zoneName,
        patientId: e.patientId.toString(),
        eventType: e.eventType,
        triggeredAt: (e.triggeredAt as Date).toISOString(),
        location: e.location,
      };
    });
    return res.json(out);
  } catch (e) {
    return handle(e, res);
  }
};

function formatGeofence(doc: any) {
  return {
    _id: doc._id.toString(),
    patientId: doc.patientId.toString(),
    caregiverId: doc.caregiverId.toString(),
    name: doc.name,
    centerLat: doc.centerLat,
    centerLng: doc.centerLng,
    radiusMeters: doc.radiusMeters,
    color: doc.color,
    isActive: doc.isActive,
    createdAt: doc.createdAt,
    updatedAt: doc.updatedAt,
  };
}

function formatGeofenceLean(e: any) {
  return {
    _id: e._id.toString(),
    patientId: e.patientId.toString(),
    caregiverId: e.caregiverId.toString(),
    name: e.name,
    centerLat: e.centerLat,
    centerLng: e.centerLng,
    radiusMeters: e.radiusMeters,
    color: e.color,
    isActive: e.isActive,
    createdAt: e.createdAt,
    updatedAt: e.updatedAt,
  };
}

function handle(e: unknown, res: Response) {
  const err = e as Error & { status?: number };
  if (err.message === "FORBIDDEN")
    return res.status(err.status || 403).json({ message: "Forbidden" });
  return res.status(500).json({ message: err.message || "Server error" });
}
