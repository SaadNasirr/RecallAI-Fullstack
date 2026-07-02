import { Router } from 'express';
import multer from 'multer';
import { handleSpeechToText, handleTextToSpeech } from '../controllers/voice.controller';
import { auth } from '../middleware/auth';

const router = Router();
const upload = multer({ dest: 'uploads/' });

router.use(auth);

router.post('/stt', upload.single('audio'), handleSpeechToText);
router.post('/tts', handleTextToSpeech);

export default router;
