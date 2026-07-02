import { Router } from "express";
import { auth } from "../middleware/auth";
import {
  getPeopleDirectory,
  putPeopleDirectory,
} from "../controllers/peopleDirectory.controller";

const router = Router();

router.use(auth);

router.get("/", getPeopleDirectory);
router.put("/", putPeopleDirectory);

export default router;
