import { Router } from "express";
import multer from "multer";
import { analyzeObjectLocator } from "../controllers/locator.controller";
import { auth } from "../middleware/auth";

const router = Router();
const upload = multer({ dest: "uploads/" });

router.use(auth);

// Route to handle object locator
router.post('/', upload.fields([
    { name: 'image', maxCount: 1 },
    { name: 'audio', maxCount: 1 }
]), analyzeObjectLocator);

export default router;
