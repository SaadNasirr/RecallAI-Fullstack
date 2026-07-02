import { Request, Response } from 'express';
import { analyzeImageWithQuery, encodeImage } from '../utils/groqVision';
import { transcribeAudioLocal } from '../utils/localVoice';
import fs from 'fs';
import { resolveSubjectUserId } from '../utils/patientScope';

export const analyzeObjectLocator = async (req: Request, res: Response) => {
    try {
        const scope = await resolveSubjectUserId(req, res, 'viewLocation');
        if (!scope) return;
        // Using multer fields: `image` and `audio`
        const files = req.files as { [fieldname: string]: Express.Multer.File[] };
        const imageFile = files?.['image']?.[0];
        const audioFile = files?.['audio']?.[0];
        const textQuery = req.body.query || '';

        if (!imageFile) {
            return res.status(400).json({ error: 'Image file is required.' });
        }

        // process audio if present
        let transcribedText = '';
        if (audioFile) {
            transcribedText = await transcribeAudioLocal(audioFile.path);
            fs.unlinkSync(audioFile.path); // clean up tmp audio
        }

        let finalQuery = textQuery;
        if (transcribedText) {
            finalQuery = `${finalQuery} (Voice: ${transcribedText})`.trim();
        }

        if (!finalQuery) {
            finalQuery = 'What is in this image?';
        }

        // Read image file and encode it base64
        const imageBuffer = fs.readFileSync(imageFile.path);
        console.log(`[locator] bytes=${imageBuffer.length} query="${finalQuery.slice(0, 120)}"`);
        if (!imageBuffer.length || imageBuffer.length < 64) {
            return res.status(400).json({ error: 'Image file empty or too small.' });
        }
        const encodedImg = encodeImage(imageBuffer);

        // cleanup image
        fs.unlinkSync(imageFile.path);

        const responseContent = await analyzeImageWithQuery(finalQuery, encodedImg);

        return res.json({
            text_query: finalQuery,
            response: responseContent,
            audio_url: null
        });

    } catch (error: any) {
        console.error('Error in object locator controller:', error);
        const msg = String(error?.message || 'Error processing request.');
        let clientMessage = msg;
        if (/model_not_found|does not exist|you do not have access|decommissioned|no longer supported/i.test(msg)) {
            clientMessage =
                'Vision analysis failed: update OBJECT_VISION_MODEL to a current Groq vision model (default: meta-llama/llama-4-scout-17b-16e-instruct). See console.groq.com/docs/deprecations.';
        } else if (/invalid api key|incorrect api key/i.test(msg)) {
            clientMessage = 'Vision service authentication failed. Check GROQ_API_KEY on the server.';
        } else if (msg.length > 240) {
            clientMessage = `${msg.slice(0, 237)}…`;
        }
        return res.status(500).json({ error: clientMessage });
    }
};
