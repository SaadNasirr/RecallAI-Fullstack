import { Router } from "express";
import { auth } from "../middleware/auth";
import { requireRole } from "../middleware/requireRole";
import {
  deleteGeofence,
  getGeofenceEvents,
  getMyZones,
  patchToggleGeofence,
  postCreateGeofence,
  postGeofenceEvent,
} from "../controllers/geofence.controller";

const router = Router();
router.use(auth);

router.post("/create", requireRole("caregiver"), postCreateGeofence);
router.get("/my-zones", getMyZones);
router.post("/event", requireRole("patient"), postGeofenceEvent);
router.get("/events/:patientId", requireRole("caregiver"), getGeofenceEvents);
router.delete("/:id", requireRole("caregiver"), deleteGeofence);
router.patch("/:id/toggle", requireRole("caregiver"), patchToggleGeofence);

export default router;
