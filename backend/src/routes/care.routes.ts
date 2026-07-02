import { Router } from "express";
import { auth } from "../middleware/auth";
import { requireRole } from "../middleware/requireRole";
import {
  deleteRelationship,
  getAlertsForCaregiver,
  getAlertsUnreadCount,
  getCareTasks,
  getMyCaregivers,
  getMyPatients,
  getPending,
  getWatchlist,
  patchAlertRead,
  patchCareTaskDone,
  patchPermissions,
  postAlertDispatch,
  postAlertsMarkAllRead,
  postApprove,
  postCareTask,
  postEmergency,
  postInviteGenerate,
  postInviteRequest,
  postPatientLiveLocation,
  postPatientShareLocation,
  postQrCreate,
  postQrScan,
  postReject,
} from "../controllers/care.controller";

const router = Router();

router.use(auth);

router.post("/qr/create", requireRole("patient"), postQrCreate);
router.post("/qr/scan", requireRole("caregiver"), postQrScan);

router.post("/invite/generate", requireRole("patient"), postInviteGenerate);
router.post("/invite/request", requireRole("caregiver"), postInviteRequest);

router.post("/request/:id/approve", requireRole("patient"), postApprove);
router.post("/request/:id/reject", requireRole("patient"), postReject);

router.get("/my-caregivers", requireRole("patient"), getMyCaregivers);
router.get("/my-patients", requireRole("caregiver"), getMyPatients);
router.get("/pending", requireRole("patient"), getPending);

router.patch("/permissions/:id", requireRole("patient"), patchPermissions);

router.post("/emergency/trigger", requireRole("patient"), postEmergency);
router.post("/patient/location", requireRole("patient"), postPatientLiveLocation);
router.post("/patient/share-address", requireRole("patient"), postPatientShareLocation);
router.post("/alerts/dispatch", requireRole("patient"), postAlertDispatch);
router.get("/alerts/inbox", requireRole("caregiver"), getAlertsForCaregiver);
router.get("/alerts/unread-count", requireRole("caregiver"), getAlertsUnreadCount);
router.patch("/alerts/:id/read", requireRole("caregiver"), patchAlertRead);
router.post("/alerts/mark-all-read", requireRole("caregiver"), postAlertsMarkAllRead);

router.get("/tasks", getCareTasks);
router.post("/tasks", requireRole("caregiver"), postCareTask);
router.patch("/tasks/:id/done", requireRole("patient"), patchCareTaskDone);

router.get("/watchlist", requireRole("caregiver"), getWatchlist);

router.delete("/relationship/:id", deleteRelationship);

export default router;
