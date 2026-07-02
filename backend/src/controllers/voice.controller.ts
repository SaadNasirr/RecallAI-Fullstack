import { Request, Response } from 'express';
import { transcribeAudioLocal, generateSpeechLocal } from '../utils/localVoice';
import fs from 'fs';
import path from 'path';

export const handleSpeechToText = async (req: Request, res: Response) => {
    try {
        const audioFile = req.file;

        if (!audioFile) {
            return res.status(400).json({ error: 'Audio file is required' });
        }

        const transcribedText = await transcribeAudioLocal(audioFile.path);

        // Clean up tmp file
        fs.unlinkSync(audioFile.path);

        res.json({ text: transcribedText });
    } catch (error: any) {
        console.error('Error in STT controller:', error);
        res.status(500).json({ error: error.message });
    }
};

export const handleTextToSpeech = async (req: Request, res: Response) => {
    try {
        const { text } = req.body;

        if (!text) {
            return res.status(400).json({ error: 'Text is required for TTS' });
        }

        const audioFilePath = await generateSpeechLocal(text);

        // Send the file back down, and delete it once finished transmitting to save space
        res.sendFile(audioFilePath, (err) => {
            if (err) {
                console.error("Error sending audio file:", err);
            }
            try {
                if (fs.existsSync(audioFilePath)) {
                    fs.unlinkSync(audioFilePath);
                }
            } catch (cleanupErr) {
                console.error("Error cleaning up audio file:", cleanupErr);
            }
        });

    } catch (error: any) {
        console.error('Error in TTS controller:', error);
        res.status(500).json({ error: error.message });
    }
};
