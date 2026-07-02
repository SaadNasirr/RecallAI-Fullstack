/** Vision/VQA for /api/locator (and default face vision). Override via OBJECT_VISION_MODEL.
 * Groq retired llama-3.2-*-vision-preview; current multimodal replacement is Llama 4 Scout.
 * @see https://console.groq.com/docs/deprecations
 */
export const OBJECT_VISION_MODEL =
  process.env.OBJECT_VISION_MODEL?.trim() ||
  "meta-llama/llama-4-scout-17b-16e-instruct";

export const FACE_VISION_MODEL =
  process.env.FACE_VISION_MODEL?.trim() || OBJECT_VISION_MODEL;

export const AUDIO_TRANSCRIBE_MODEL =
  process.env.AUDIO_TRANSCRIBE_MODEL?.trim() || "whisper-large-v3";

export const RECALL_REASONING_MODEL =
  process.env.RECALL_REASONING_MODEL?.trim() || "llama-3.1-8b-instant";

export const MEMORY_ANALYSIS_MODEL =
  process.env.MEMORY_ANALYSIS_MODEL?.trim() || "llama-3.3-70b-versatile";

export const modelConfig = {
  objectVision: OBJECT_VISION_MODEL,
  faceVision: FACE_VISION_MODEL,
  audioTranscribe: AUDIO_TRANSCRIBE_MODEL,
  recallReasoning: RECALL_REASONING_MODEL,
  memoryAnalysis: MEMORY_ANALYSIS_MODEL,
};
