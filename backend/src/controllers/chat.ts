import { Request, Response } from "express";
import { ChatSession } from "../models/ChatSession";
import { GoogleGenerativeAI } from "@google/generative-ai";
import Groq from "groq-sdk";
import { v4 as uuidv4 } from "uuid";
import { logger } from "../utils/logger";
import { withTimeout } from "../utils/asyncUtils";
import { resolveSubjectUserId } from "../utils/patientScope";

let _gemini: GoogleGenerativeAI | null = null;
let _groq: Groq | null = null;
function readGeminiKey(): string | null {
  const key = process.env.GEMINI_API_KEY?.trim();
  return key ? key : null;
}
function readGroqKey(): string | null {
  const key = process.env.GROQ_API_KEY?.trim();
  return key ? key : null;
}
function isQuotaError(err: any): boolean {
  const status = err?.status;
  const msg = String(err?.message || "").toLowerCase();
  return status === 429 || msg.includes("quota") || msg.includes("too many requests");
}
function isInvalidGroqKey(err: any): boolean {
  const msg = String(err?.message || "").toLowerCase();
  const code = String(err?.error?.error?.code || err?.error?.code || "");
  return code === "invalid_api_key" || msg.includes("invalid api key") || err?.status === 401;
}
function localTherapyFallback(userMessage: string): {
  response: string;
  analysis: {
    emotionalState: string;
    themes: string[];
    riskLevel: number;
    recommendedApproach: string;
    progressIndicators: string[];
  };
} {
  const text = userMessage.toLowerCase();
  const riskWords = ["suicide", "kill myself", "self harm", "hurt myself", "end my life"];
  const anxietyWords = ["anxious", "anxiety", "panic", "nervous", "overwhelmed", "stress"];
  const sadWords = ["sad", "depressed", "empty", "hopeless", "lonely"];
  const sleepWords = ["sleep", "insomnia", "can't sleep", "cant sleep", "nightmare"];

  const isRisk = riskWords.some((w) => text.includes(w));
  const isAnxious = anxietyWords.some((w) => text.includes(w));
  const isSad = sadWords.some((w) => text.includes(w));
  const isSleep = sleepWords.some((w) => text.includes(w));

  if (isRisk) {
    return {
      response:
        "Thank you for telling me this. You matter, and your safety is the top priority right now. If you might act on these thoughts, please call your local emergency number now or contact a crisis helpline immediately. If you want, I can stay with you while we make a simple safety plan for the next 10 minutes.",
      analysis: {
        emotionalState: "high distress",
        themes: ["safety", "crisis"],
        riskLevel: 9,
        recommendedApproach: "safety-first crisis support",
        progressIndicators: ["shared difficult feelings", "accepted immediate support"],
      },
    };
  }

  if (isAnxious) {
    return {
      response:
        "I hear you. Let us slow this down together for one minute. Try this now: inhale for 4 seconds, hold for 4, exhale for 6. Repeat 4 rounds. Then name 5 things you see, 4 you can touch, 3 you hear, 2 you smell, and 1 you taste. If you want, tell me what thought is making the anxiety strongest right now and we will unpack it step by step.",
      analysis: {
        emotionalState: "anxious",
        themes: ["anxiety", "grounding"],
        riskLevel: 2,
        recommendedApproach: "grounding + paced breathing",
        progressIndicators: ["asked for help", "open to regulation exercise"],
      },
    };
  }

  if (isSad) {
    return {
      response:
        "That sounds heavy, and I am glad you shared it. We can take this gently. Could you tell me one moment today that felt hardest? After that, we can choose one tiny action for the next hour to make things 1% easier.",
      analysis: {
        emotionalState: "low mood",
        themes: ["sadness", "support"],
        riskLevel: 3,
        recommendedApproach: "validation + behavioral activation",
        progressIndicators: ["expressed feelings", "engaging in reflection"],
      },
    };
  }

  if (isSleep) {
    return {
      response:
        "Sleep problems can make everything feel harder. For tonight, try a short wind-down: no phone for 30 minutes, dim lights, slow breathing for 2 minutes, and write tomorrow's worries on paper so your mind does not hold them in bed. Want a personalized bedtime routine in 3 simple steps?",
      analysis: {
        emotionalState: "fatigued/stressed",
        themes: ["sleep", "routine"],
        riskLevel: 1,
        recommendedApproach: "sleep hygiene coaching",
        progressIndicators: ["identified practical concern"],
      },
    };
  }

  return {
    response:
      "Thank you for opening up. I am here with you. Tell me what feels most difficult right now, and what outcome you want by the end of this chat. We will break it into small, manageable steps together.",
    analysis: {
      emotionalState: "neutral",
      themes: ["support", "clarification"],
      riskLevel: 0,
      recommendedApproach: "client-centered supportive counseling",
      progressIndicators: ["engaged in conversation"],
    },
  };
}
function getGemini(): GoogleGenerativeAI {
  if (!_gemini) {
    _gemini = new GoogleGenerativeAI(readGeminiKey() || "");
  }
  return _gemini;
}
function getGroq(): Groq {
  if (!_groq) {
    _groq = new Groq({ apiKey: readGroqKey() || undefined });
  }
  return _groq;
}

// Get all chat sessions for the authenticated user
export const getAllSessions = async (req: Request, res: Response) => {
  try {
    const subjectId = await resolveSubjectUserId(req, res, "viewMemories");
    if (!subjectId) return;
    const sessions = await ChatSession.find({ userId: subjectId })
      .sort({ startTime: -1 })
      .lean();
    res.json(sessions);
  } catch (error) {
    logger.error("Error fetching all sessions:", error);
    res.status(500).json({ message: "Error fetching sessions" });
  }
};

// Create a new chat session
export const createChatSession = async (req: Request, res: Response) => {
  try {
    if (!req.user || !req.user.id) {
      return res
        .status(401)
        .json({ message: "Unauthorized - User not authenticated" });
    }

    const subjectId = await resolveSubjectUserId(req, res, "viewMemories");
    if (!subjectId) return;

    const sessionId = uuidv4();

    const session = new ChatSession({
      sessionId,
      userId: subjectId,
      startTime: new Date(),
      status: "active",
      messages: [],
    });

    await session.save();

    res.status(201).json({
      message: "Chat session created successfully",
      sessionId: session.sessionId,
    });
  } catch (error) {
    logger.error("Error creating chat session:", error);
    res.status(500).json({
      message: "Error creating chat session",
      error: error instanceof Error ? error.message : "Unknown error",
    });
  }
};

// Send a message in the chat session
export const sendMessage = async (req: Request, res: Response) => {
  try {
    const { sessionId } = req.params;
    const { message } = req.body;
    const subjectId = await resolveSubjectUserId(req, res, "viewMemories");
    if (!subjectId) return;

    logger.info("Processing message:", { sessionId, message });

    // Fetch only what we need: ownership field + last 6 messages for context.
    // $slice: -6 returns the tail of the array in MongoDB — avoids transferring the full
    // message history for long sessions. We use lean() and a separate updateOne() below.
    const session = await ChatSession.findOne(
      { sessionId },
      { userId: 1, messages: { $slice: -6 } } as any
    ).lean();
    if (!session) {
      logger.warn("Session not found:", { sessionId });
      return res.status(404).json({ message: "Session not found" });
    }

    if (session.userId.toString() !== subjectId.toString()) {
      logger.warn("Unauthorized access attempt:", { sessionId, subjectId });
      return res.status(403).json({ message: "Unauthorized" });
    }

    const historyText = session.messages
      .map((m) => `${m.role === "user" ? "User" : "Recall"}: ${m.content}`)
      .join("\n");

    const combinedPrompt = `Conversation so far:
${historyText || "(new conversation)"}

User: ${message}

Respond ONLY with this JSON (no markdown, no backticks, no extra text):
{
  "response": "<your reply — warm, simple, max 3 sentences>",
  "analysis": {
    "emotionalState": "<calm|confused|anxious|sad|happy|frustrated|unknown>",
    "themes": ["<1-3 short topic words>"],
    "riskLevel": <0-10 integer>,
    "recommendedApproach": "<reassuring|informational|playful|gentle|alert>",
    "progressIndicators": ["<facts from this message worth noting, or empty>"]
  }
}`;

    let response: string | undefined;
    let analysis: any;

    const groqKey = readGroqKey();
    const geminiKey = readGeminiKey();

    try {
      const t0 = Date.now();
      let parsed: any;

      if (groqKey) {
        const completion = await withTimeout(
          getGroq().chat.completions.create({
            model: "llama-3.1-8b-instant",
            temperature: 0.3,
            response_format: { type: "json_object" },
            messages: [
              {
                role: "system",
                content: `You are Recall, a warm and patient AI companion for people with memory difficulties. Your user may be elderly and may have dementia or mild cognitive impairment. Speak simply — short sentences, one idea at a time, like a caring family member would.

Core rules:
- Never use clinical or technical language.
- If the user seems confused or lost, gently reassure them first.
- If the user mentions a safety concern (lost, fallen, alone, scared), acknowledge it warmly and suggest they contact their caregiver.
- Keep your response under 3 sentences unless the user asks for more.
- riskLevel: 0=calm, 5=distressed, 8+=safety concern.

Return ONLY valid JSON, no markdown, no backticks.`,
              },
              { role: "user", content: combinedPrompt },
            ],
          }),
          90_000,
          "Groq therapist completion"
        );
        const raw = (completion.choices?.[0]?.message?.content || "{}").trim();
        parsed = JSON.parse(raw);
      } else if (geminiKey) {
        const model = getGemini().getGenerativeModel({ model: "gemini-2.0-flash" });
        const result = await withTimeout(
          model.generateContent(combinedPrompt),
          90_000,
          "Gemini therapist completion"
        );
        const text = result.response.text().trim();
        const clean = text.replace(/```json\s*|\s*```/g, "").trim();
        parsed = JSON.parse(clean);
      } else {
        throw new Error("No AI provider key configured");
      }

      response =
        parsed.response ||
        "I'm here to support you. Could you tell me more?";
      analysis =
        parsed.analysis || {
          emotionalState: "neutral",
          themes: [],
          riskLevel: 0,
          recommendedApproach: "supportive",
          progressIndicators: [],
        };

    } catch (aiError) {
      const errMessage = aiError instanceof Error ? aiError.message : String(aiError);

      // Groq -> Gemini fallback
      if (groqKey && (isInvalidGroqKey(aiError) || isQuotaError(aiError)) && geminiKey) {
        try {
          const model = getGemini().getGenerativeModel({ model: "gemini-2.0-flash" });
          const result = await withTimeout(
            model.generateContent(combinedPrompt),
            90_000,
            "Gemini therapist fallback completion"
          );
          const text = result.response.text().trim();
          const clean = text.replace(/```json\s*|\s*```/g, "").trim();
          const parsed = JSON.parse(clean);

          response =
            parsed.response ||
            "I'm here to support you. Could you tell me more?";
          analysis =
            parsed.analysis || {
              emotionalState: "neutral",
              themes: [],
              riskLevel: 0,
              recommendedApproach: "supportive",
              progressIndicators: [],
            };
        } catch (geminiError) {
          if (isQuotaError(geminiError)) {
            logger.warn("Gemini quota exceeded after Groq failure; using local fallback.");
          } else {
            const gm = geminiError instanceof Error ? geminiError.message : String(geminiError);
            logger.warn("Gemini unavailable after Groq failure; using local fallback.", {
              error: gm.slice(0, 220),
            });
          }
          const fallback = localTherapyFallback(message);
          response = fallback.response;
          analysis = fallback.analysis;
        }
      } else if (isQuotaError(aiError)) {
        logger.warn("AI provider quota exceeded; using local therapist fallback.");
        const fallback = localTherapyFallback(message);
        response = fallback.response;
        analysis = fallback.analysis;
      } else {
        logger.warn("AI provider unavailable; using local therapist fallback.", {
          error: errMessage.slice(0, 220),
        });
        const fallback = localTherapyFallback(message);
        response = fallback.response;
        analysis = fallback.analysis;
      }
    }

    logger.info("Generated response successfully");

    // Atomically append the two new messages without re-reading the full document.
    await ChatSession.updateOne(
      { sessionId },
      {
        $push: {
          messages: {
            $each: [
              { role: "user", content: message, timestamp: new Date() },
              {
                role: "assistant",
                content: response || "I'm here to support you. Could you tell me more?",
                timestamp: new Date(),
                metadata: {
                  analysis,
                  progress: {
                    emotionalState: analysis.emotionalState,
                    riskLevel: analysis.riskLevel,
                  },
                },
              },
            ],
          },
        },
      }
    );
    logger.info("Session updated successfully:", { sessionId });

    res.json({
      response,
      message: response,
      analysis,
      metadata: {
        progress: {
          emotionalState: analysis.emotionalState,
          riskLevel: analysis.riskLevel,
        },
      },
    });
  } catch (error) {
    logger.error("Error in sendMessage:", error);
    res.status(500).json({
      message: "Error processing message",
      error: error instanceof Error ? error.message : "Unknown error",
    });
  }
};

// Get chat session history (optional helper — route uses getChatHistory by session UUID)
export const getSessionHistory = async (req: Request, res: Response) => {
  try {
    const { sessionId } = req.params;
    const subjectId = await resolveSubjectUserId(req, res, "viewMemories");
    if (!subjectId) return;

    const session = await ChatSession.findOne({ sessionId });
    if (!session) {
      return res.status(404).json({ message: "Session not found" });
    }

    if (session.userId.toString() !== subjectId.toString()) {
      return res.status(403).json({ message: "Unauthorized" });
    }

    res.json({
      messages: session.messages,
      startTime: session.startTime,
      status: session.status,
    });
  } catch (error) {
    logger.error("Error fetching session history:", error);
    res.status(500).json({ message: "Error fetching session history" });
  }
};

export const getChatSession = async (req: Request, res: Response) => {
  try {
    const { sessionId } = req.params;
    const subjectId = await resolveSubjectUserId(req, res, "viewMemories");
    if (!subjectId) return;
    logger.info(`Getting chat session: ${sessionId}`);
    const chatSession = await ChatSession.findOne({ sessionId });
    if (!chatSession) {
      logger.warn(`Chat session not found: ${sessionId}`);
      return res.status(404).json({ error: "Chat session not found" });
    }
    if (chatSession.userId.toString() !== subjectId.toString()) {
      return res.status(403).json({ error: "Unauthorized" });
    }
    logger.info(`Found chat session: ${sessionId}`);
    res.json(chatSession);
  } catch (error) {
    logger.error("Failed to get chat session:", error);
    res.status(500).json({ error: "Failed to get chat session" });
  }
};

export const getChatHistory = async (req: Request, res: Response) => {
  try {
    const { sessionId } = req.params;
    const subjectId = await resolveSubjectUserId(req, res, "viewMemories");
    if (!subjectId) return;

    const session = await ChatSession.findOne({ sessionId });
    if (!session) {
      return res.status(404).json({ message: "Session not found" });
    }

    if (session.userId.toString() !== subjectId.toString()) {
      return res.status(403).json({ message: "Unauthorized" });
    }

    res.json(session.messages);
  } catch (error) {
    logger.error("Error fetching chat history:", error);
    res.status(500).json({ message: "Error fetching chat history" });
  }
};