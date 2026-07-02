import { Router } from "express";
import multer from "multer";
import { analyzeFace, enrollFace, recognizeFace } from "../controllers/face.controller";
import { auth } from "../middleware/auth";

const router = Router();
const upload = multer({ dest: "uploads/" });

router.use(auth);

router.post("/", upload.single("image"), analyzeFace);router.post("/enroll", upload.single("image"), enrollFace);
router.post("/recognize", upload.single("image"), recognizeFace);

export default router;

