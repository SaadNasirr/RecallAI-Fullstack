import { Router } from "express";
import { auth } from "../middleware/auth";
import { getMemories, addMemory, deleteMemory } from "../controllers/memory";

const router = Router();

router.use(auth);

router.get("/", getMemories);
router.post("/", addMemory);
router.delete("/:id", deleteMemory);

export default router;
