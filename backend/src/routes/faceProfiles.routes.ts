import { Router } from "express";
import { auth } from "../middleware/auth";
import {
  getFaceProfiles,
  putFaceProfiles,
} from "../controllers/faceProfiles.controller";

const router = Router();

router.use(auth);

router.get("/", getFaceProfiles);
router.put("/", putFaceProfiles);

export default router;
