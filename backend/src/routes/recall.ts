import { Router } from "express";
import { auth } from "../middleware/auth";
import { recallChat } from "../controllers/recall";

const router = Router();

router.use(auth);

router.post("/chat", recallChat);

export default router;
