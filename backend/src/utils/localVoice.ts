import fs from 'fs';
import axios from 'axios';
import FormData from 'form-data';
import path from 'path';

const PYTHON_SIDECAR_URL = 'http://localhost:8000';

/**
 * Encodes a buffer to a base64 string
 */
export const encodeImage = (imageBuffer: Buffer): string => {
    return imageBuffer.toString('base64');
};

/**
 * Transcribes audio using the local Python faster-whisper sidecar.
 * Input expects the file path to the audio file.
 */
export const transcribeAudioLocal = async (audioFilePath: string): Promise<string> => {
    try {
        const postOnce = () => {
            const formData = new FormData();
            formData.append('audio', fs.createReadStream(audioFilePath), {
                filename: path.basename(audioFilePath),
                contentType: 'audio/m4a',
            });
            return axios.post(`${PYTHON_SIDECAR_URL}/stt`, formData, {
                headers: {
                    ...formData.getHeaders(),
                },
                timeout: 35_000
            });
        };
        let response;
        try {
            response = await postOnce();
        } catch (firstError: any) {
            const msg = String(firstError?.message || '').toLowerCase();
            const retryable = msg.includes('timeout') || msg.includes('socket hang up') || msg.includes('econnreset');
            if (!retryable) throw firstError;
            // One retry to absorb occasional local sidecar hiccups.
            response = await postOnce();
        }

        return response.data.text;
    } catch (error: any) {
        console.error('Error invoking local STT:', error.message);
        throw new Error(`Local STT failed: ${error.message}`);
    }
};

/**
 * Generates speech using the local Python piper-tts sidecar.
 * Input expects text, outputs the path to the generated audio file.
 */
export const generateSpeechLocal = async (
    text: string,
    outputFilename: string = `response_${Date.now()}.wav`
): Promise<string> => {
    try {
        const response = await axios.post(
            `${PYTHON_SIDECAR_URL}/tts`,
            { text },
            { responseType: 'stream' }
        );

        const savePath = path.join(process.cwd(), 'uploads', outputFilename);

        // Ensure uploads directory exists
        if (!fs.existsSync(path.dirname(savePath))) {
            fs.mkdirSync(path.dirname(savePath), { recursive: true });
        }

        const writer = fs.createWriteStream(savePath);
        response.data.pipe(writer);

        return new Promise((resolve, reject) => {
            writer.on('finish', () => resolve(savePath));
            writer.on('error', reject);
        });
    } catch (error: any) {
        console.error('Error invoking local TTS:', error.message);
        throw new Error(`Local TTS failed: ${error.message}`);
    }
};
