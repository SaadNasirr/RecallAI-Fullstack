import fs from 'fs';
import { Groq } from 'groq-sdk';
import { AUDIO_TRANSCRIBE_MODEL, OBJECT_VISION_MODEL } from '../config/modelConfig';

const readGroqKey = () => process.env.GROQ_API_KEY?.trim();

let _groqVision: Groq | null = null;
const getGroqClient = (): Groq => {
    if (!_groqVision) {
        _groqVision = new Groq({ apiKey: readGroqKey() });
    }
    return _groqVision;
};

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

const parseRetryAfterMs = (error: any): number => {
    const retryHeader =
        error?.headers?.["retry-after"] ||
        error?.response?.headers?.["retry-after"] ||
        error?.error?.headers?.["retry-after"];
    const retrySec = Number(retryHeader);
    if (!Number.isNaN(retrySec) && retrySec > 0) return Math.round(retrySec * 1000);

    const msg = String(error?.message || "");
    const m = msg.match(/Please try again in ([0-9.]+)s/i);
    if (m) {
        const sec = Number(m[1]);
        if (!Number.isNaN(sec) && sec > 0) return Math.round(sec * 1000);
    }
    return 1200;
};

/**
 * Encodes a buffer to a base64 string
 */
export const encodeImage = (imageBuffer: Buffer): string => {
    return imageBuffer.toString('base64');
};

/** Groq-recommended replacement for deprecated llama-3.2 vision previews (see deprecations doc). */
const VISION_FALLBACK_MODEL = 'meta-llama/llama-4-scout-17b-16e-instruct';

const isModelUnavailableError = (error: any): boolean => {
    const status = Number(error?.status || error?.response?.status || 0);
    const msg = String(error?.message || '');
    const nestedCode = error?.error?.error?.code || error?.error?.code;
    return (
        nestedCode === 'model_not_found' ||
        nestedCode === 'model_decommissioned' ||
        msg.includes('model_not_found') ||
        msg.includes('model_decommissioned') ||
        msg.includes('decommissioned') ||
        msg.includes('no longer supported') ||
        msg.includes('does not exist') ||
        msg.includes('you do not have access') ||
        (status === 404 && msg.includes('model')) ||
        (status === 400 && (nestedCode === 'model_decommissioned' || msg.includes('decommissioned')))
    );
};

const OBJECT_VISION_SYSTEM_PROMPT =
    'You are a helpful object-finding assistant for an elderly person who may have memory difficulties. ' +
    'They are photographing their surroundings to find a specific object or understand what is around them. ' +
    'Scan the entire frame: floor, shelves, tables, chairs, counters, walls. ' +
    'Look for the specific object the user mentions, even if it is partially hidden, in shadow, or small. ' +
    'If found: clearly state WHERE it is in the image (e.g. "on the left side of the table", "near the door on the floor"). ' +
    'If not found: say so plainly and suggest 1-2 other places to check. ' +
    'Keep your response under 3 sentences. Be direct and simple. ' +
    'Never say "I cannot see" if an image was provided.';

/**
 * Analyzes an image with a text query using Groq's Vision model.
 * Pass systemPrompt to override the default object-detection instructions
 * (e.g. for face emotion analysis which has different requirements).
 * Retries on rate limits; falls back to a known vision model if the configured one is inaccessible.
 */
export const analyzeImageWithQuery = async (
    query: string,
    encodedImage: string,
    model: string = OBJECT_VISION_MODEL,
    systemPrompt?: string
) => {
    const groq = getGroqClient();
    const modelsToTry = [...new Set([model, OBJECT_VISION_MODEL, VISION_FALLBACK_MODEL].filter(Boolean))];
    let lastError: any;
    const maxAttempts = 3;

    const effectiveSystemPrompt = systemPrompt ?? OBJECT_VISION_SYSTEM_PROMPT;

    for (const visionModel of modelsToTry) {
        for (let attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                const chatCompletion = await groq.chat.completions.create({
                    messages: [
                        {
                            role: 'system',
                            content: effectiveSystemPrompt,
                        },
                        {
                            role: 'user',
                            // @ts-ignore - Groq SDK multimodal content
                            content: [
                                {
                                    type: 'text',
                                    text: query,
                                },
                                {
                                    type: 'image_url',
                                    image_url: {
                                        url: `data:image/jpeg;base64,${encodedImage}`,
                                    },
                                },
                            ],
                        },
                    ],
                    model: visionModel,
                    temperature: 0.7,
                    max_tokens: 500,
                });

                return chatCompletion.choices[0]?.message?.content || 'No response generated';
            } catch (error: any) {
                lastError = error;
                const status = Number(error?.status || error?.response?.status || 0);
                if (status === 429 && attempt < maxAttempts) {
                    const waitMs = parseRetryAfterMs(error);
                    await sleep(waitMs);
                    continue;
                }
                if (isModelUnavailableError(error)) {
                    console.warn(
                        `[groqVision] vision model "${visionModel}" unavailable; trying next fallback if any.`
                    );
                    break;
                }

                console.error('Error analyzing image:', error);
                const failStatus = Number(error?.status || error?.response?.status || 0);
                const wrapped: any = new Error(`Error analyzing image: ${error?.message || 'Unknown error'}`);
                if (failStatus > 0) wrapped.status = failStatus;
                if (failStatus === 429) wrapped.retryAfterMs = parseRetryAfterMs(error);
                throw wrapped;
            }
        }
    }

    console.error('Error analyzing image:', lastError);
    const status = Number(lastError?.status || lastError?.response?.status || 0);
    const wrapped: any = new Error(`Error analyzing image: ${lastError?.message || 'Unknown error'}`);
    if (status > 0) wrapped.status = status;
    if (status === 429) wrapped.retryAfterMs = parseRetryAfterMs(lastError);
    throw wrapped;
};

/**
 * Transcribes audio using Groq's Whisper model.
 * Input expects the file path to the audio file.
 */
export const transcribeAudio = async (
    audioFilePath: string,
    model: string = AUDIO_TRANSCRIBE_MODEL
) => {
    try {
        const groq = getGroqClient();
        const transcription = await groq.audio.transcriptions.create({
            model: model,
            file: fs.createReadStream(audioFilePath),
            language: 'en',
        });
        return transcription.text;
    } catch (error: any) {
        console.error('Error transcribing audio:', error);
        throw new Error(`Error transcribing audio: ${error.message}`);
    }
};
