import { Request, Response } from "express";
import { Types } from "mongoose";
import { Memory } from "../models/Memory";
import { generateEmbedding } from "../utils/embeddings";
import { analyzeMemoryContent } from "../utils/groqRecall";
import { logger } from "../utils/logger";
import { resolveSubjectUserId } from "../utils/patientScope";

// GET /memory — get all memories for a user (with optional search/type filter)
export const getMemories = async (req: Request, res: Response) => {
  try {
    const userId = await resolveSubjectUserId(req, res, "viewMemories");
    if (!userId) return;
    const { search, type_filter } = req.query;

    const query: any = { userId };
    if (type_filter && typeof type_filter === "string") {
      query.type = type_filter;
    }
    if (search && typeof search === "string" && search.trim()) {
      query.rawText = { $regex: search.trim(), $options: "i" };
    }

    const memories = await Memory.find(query)
      .select("-embedding") // don't send 384 floats to the client!
      .sort({ createdAt: -1 })
      .lean();

    res.json(memories);
  } catch (error) {
    logger.error("Error fetching memories:", error);
    res.status(500).json({ message: "Error fetching memories" });
  }
};

// POST /memory — save a new memory
export const addMemory = async (req: Request, res: Response) => {
  try {
    const userId = await resolveSubjectUserId(req, res, "manageReminders");
    if (!userId) return;
    const { text } = req.body;

    if (!text || typeof text !== "string" || !text.trim()) {
      return res.status(400).json({ message: "Memory text is required" });
    }

    // Idempotency — two-tier dedup to prevent duplicates from multi-tap or sync races:
    //
    // Tier 1: structured health events (medication, routine, people, emergency) that are
    //   semantically idempotent within a calendar day — "marked taken at 12:05 AM" should only
    //   ever appear once, no matter how many times the Android client re-uploads it.
    const isStructuredEvent =
      text.includes("marked taken") ||
      text.includes("Medication scheduled:") ||
      text.includes("Medication adherence") ||
      text.includes("Medication skip") ||
      text.includes("Routine task") ||
      text.includes("Known person") ||
      text.includes("People Directory") ||
      text.includes("Emergency SOS");
    if (isStructuredEvent) {
      const todayStart = new Date();
      todayStart.setHours(0, 0, 0, 0);
      const sameDayDupe = await Memory.findOne(
        { userId, originalInput: text, createdAt: { $gte: todayStart } },
        { embedding: 0 }
      ).lean();
      if (sameDayDupe) return res.status(201).json(sameDayDupe);
    }

    // Tier 2: any memory — 5-minute window catches sync-race re-uploads of non-structured text.
    const recentDupe = await Memory.findOne(
      { userId, originalInput: text, createdAt: { $gte: new Date(Date.now() - 300_000) } },
      { embedding: 0 }
    ).lean();
    if (recentDupe) {
      return res.status(201).json(recentDupe);
    }

    // Caregiver-assigned tasks: keep exact structured body so patient apps can parse Title/Time/Due lines.
    if (text.includes("[CARE_TASK]")) {
      const timestamp = new Date().toISOString().replace("T", " ").substring(0, 19);
      const embedding = await generateEmbedding(text);
      const memory = new Memory({
        userId,
        text: `[${timestamp}] ${text}`,
        rawText: text,
        originalInput: text,
        timestamp,
        eventTime: timestamp,
        type: "note",
        tags: ["care_task", "caregiver"],
        embedding,
      });
      await memory.save();
      const { embedding: _e, ...memoryData } = memory.toObject();
      return res.status(201).json(memoryData);
    }

    logger.info("Analyzing memory content...");

    // 1. Analyze with Groq (exact Recall AI logic)
    const t0 = Date.now();
    const analysis = await analyzeMemoryContent(text);
    const tAnalyzeMs = Date.now() - t0;
    const timestamp = new Date().toISOString().replace("T", " ").substring(0, 19);

    const eventTime = analysis.event_time || timestamp;
    const normalizedText = analysis.normalized_text || text;
    const memoryType = analysis.memory_type || "note";
    const tags = analysis.tags || [];

    const fullText = `[${eventTime}] ${normalizedText}`;

    // 2. Generate embedding
    logger.info("Generating embedding...");
    const t1 = Date.now();
    const embedding = await generateEmbedding(normalizedText);
    const tEmbedMs = Date.now() - t1;

    // 3. Save to MongoDB
    const memory = new Memory({
      userId,
      text: fullText,
      rawText: normalizedText,
      originalInput: text,
      timestamp,
      eventTime,
      type: memoryType,
      tags,
      embedding,
    });

    await memory.save();
    logger.info("Memory saved:", { id: memory._id, type: memoryType, tAnalyzeMs, tEmbedMs });

    // Return without embedding field
    const { embedding: _e, ...memoryData } = memory.toObject();
    res.status(201).json(memoryData);
  } catch (error) {
    logger.error("Error adding memory:", error);
    res.status(500).json({ message: "Error saving memory" });
  }
};

// DELETE /memory/:id — delete a memory
export const deleteMemory = async (req: Request, res: Response) => {
  try {
    const userId = await resolveSubjectUserId(req, res, "manageReminders");
    if (!userId) return;
    const { id } = req.params;

    const memory = await Memory.findOneAndDelete({ _id: id, userId });
    if (!memory) {
      return res.status(404).json({ message: "Memory not found" });
    }

    res.json({ status: "deleted" });
  } catch (error) {
    logger.error("Error deleting memory:", error);
    res.status(500).json({ message: "Error deleting memory" });
  }
};
