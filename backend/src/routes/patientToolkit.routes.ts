import { Router } from "express";
import { auth } from "../middleware/auth";
import {
  getPatientToolkit,
  putPatientToolkit,
} from "../controllers/patientToolkit.controller";

const router = Router();

router.use(auth);

router.get("/", getPatientToolkit);
router.put("/", putPatientToolkit);

export default router;
