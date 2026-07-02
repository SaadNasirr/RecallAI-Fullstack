import { Request, Response } from "express";
import fs from "fs";
import { analyzeImageWithQuery, encodeImage } from "../utils/groqVision";
import { FACE_VISION_MODEL } from "../config/modelConfig";
import { resolveSubjectUserId } from "../utils/patientScope";

export const analyzeFace = async (req: Request, res: Response) => {
  try {
    const subject = await resolveSubjectUserId(req, res, "viewMemories");
    if (!subject) return;

    const imageFile = req.file;
    const contextHint = (req.body?.contextHint || "").toString().trim();

    if (!imageFile) {
      return res.status(400).json({ error: "Image file is required." });
    }

    const imageBuffer = fs.readFileSync(imageFile.path);
    const encoded = encodeImage(imageBuffer);
    fs.unlinkSync(imageFile.path);

    const faceSystemPrompt = `You analyze facial expressions for a memory care app that supports elderly patients with dementia. This analysis helps caregivers monitor patient wellbeing remotely.

Analyze the face(s) in this image and return ONLY this JSON (no markdown, no backticks):
{
  "faceCount": <integer>,
  "dominantMood": "<happy|calm|confused|sad|anxious|tired|upset|unknown>",
  "confidence": <0.0 to 1.0>,
  "observations": ["<max 8 words>", "<max 8 words>"],
  "careSuggestion": "<one short observation a caregiver would find useful, or null>"
}

Rules:
- If no face is detected: faceCount=0, dominantMood="unknown", confidence=0.
- observations must be factual and non-diagnostic (max 2 items, max 8 words each).
- careSuggestion is null when there is nothing notable to report.${contextHint ? `\n- Caregiver context: ${contextHint}` : ""}`;

    const query = "Analyze the facial expression(s) in this image.";

    const raw = await analyzeImageWithQuery(query, encoded, FACE_VISION_MODEL, faceSystemPrompt);
    const cleaned = (raw || "").replace(/```json|```/g, "").trim();

    let parsed: any;
    try {
      parsed = JSON.parse(cleaned);
    } catch {
      parsed = {
        faceCount: 0,
        dominantMood: "unknown",
        confidence: 0.2,
        observations: [String(raw || "Unable to parse model output").slice(0, 140)],
        careSuggestion: "Try another image with clearer face visibility.",
      };
    }

    return res.json(parsed);
  } catch (error: any) {
    console.error("Error in face analysis controller:", error);
    if (Number(error?.status) === 429) {
      const retryAfterSec = Math.max(1, Math.ceil((Number(error?.retryAfterMs || 1200)) / 1000));
      res.setHeader("Retry-After", String(retryAfterSec));
      return res.status(429).json({
        error: "Face model is rate-limited. Please retry in a few seconds.",
        code: "rate_limit_exceeded",
      });
    }
    return res.status(500).json({ error: error?.message || "Face analysis failed." });
  }
};

export const enrollFace = async (req: Request, res: Response) => {
  const imageFile = req.file;
  if (imageFile?.path) {
    try {
      fs.unlinkSync(imageFile.path);
    } catch {
      /* ignore */
    }
  }
  return res.status(410).json({
    error: "Face enrollment runs on-device in the RecallAI app.",
    code: "identity_on_device",
  });
};

export const recognizeFace = async (req: Request, res: Response) => {
  const imageFile = req.file;
  if (imageFile?.path) {
    try {
      fs.unlinkSync(imageFile.path);
    } catch {
      /* ignore */
    }
  }
  return res.status(410).json({
    error: "Face recognition runs on-device in the RecallAI app.",
    code: "identity_on_device",
  });
};

