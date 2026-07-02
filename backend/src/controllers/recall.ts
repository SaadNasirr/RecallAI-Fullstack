import { Request, Response } from "express";
import { Memory } from "../models/Memory";
import {
  generateEmbedding,
  localFallbackEmbedding,
  vectorSearch,
} from "../utils/embeddings";
import { withTimeout } from "../utils/asyncUtils";
import {
  detectRetrievalIntent,
  normalizeQueryWithLLM,
  generateRecallResponse,
} from "../utils/groqRecall";
import { logger } from "../utils/logger";
import { resolveSubjectUserId } from "../utils/patientScope";

const TIME_KEYWORDS = [
  "today", "tomorrow", "yesterday", "next", "last", "week",
  "month", "year", "monday", "tuesday", "wednesday", "thursday",
  "friday", "saturday", "sunday",
];

// POST /recall/chat — FAISS-style search + Groq response
export const recallChat = async (req: Request, res: Response) => {
  try {
    const userId = await resolveSubjectUserId(req, res, "viewMemories");
    if (!userId) return;
    const { query } = req.body;

    if (!query || typeof query !== "string" || !query.trim()) {
      return res.status(400).json({ message: "Query is required" });
    }

    logger.info("Recall chat query:", { query });

    const hasTimeKeyword = TIME_KEYWORDS.some((k) =>
      query.toLowerCase().includes(k)
    );

    if (hasTimeKeyword) {
      logger.info("Time context detected, normalizing query...");
    }

    // Parallelize independent work: query normalization (when needed), intent, and DB load.
    const [searchQuery, filterType, allMemoriesRaw] = await Promise.all([
      hasTimeKeyword ? normalizeQueryWithLLM(query) : Promise.resolve(query),
      detectRetrievalIntent(query),
      Memory.find({ userId }).lean(),
    ]);
    logger.info("Search query:", { searchQuery });
    logger.info("Intent filter:", { filterType });

    // exclude "recall of recall" entries (Android used to save recall Q/A back into /memory).
    const allMemories = allMemoriesRaw.filter((m: any) => {
      const rawText = String(m?.rawText || "");
      const originalInput = String(m?.originalInput || "");
      return !rawText.startsWith("Recall Query:") && !originalInput.startsWith("Recall Query:");
    });

    if (allMemories.length === 0) {
      return res.json({
        response: "You haven't saved any memories yet. Go to the Memory Bank to add some!",
        sources: [],
      });
    }

    // 4. Generate query embedding (bounded wait; fallback keeps recall responsive if HF stalls)
    let queryVec: number[];
    try {
      queryVec = await withTimeout(
        generateEmbedding(searchQuery),
        45_000,
        "Embedding generation"
      );
    } catch (e) {
      logger.warn("Embedding slow/failed; using local fallback vector", e);
      queryVec = localFallbackEmbedding(String(searchQuery));
    }

    const K = 5;

    // ATTEMPT 1: Search with type filter (exact Recall AI logic)
    let filteredResults: any[] = [];
    if (filterType) {
      filteredResults = vectorSearch(queryVec, allMemories as any, K, filterType);
    }

    // ATTEMPT 2: Always search unfiltered (exact Recall AI logic)
    const unfilteredResults = vectorSearch(queryVec, allMemories as any, K, undefined);

    // Merge and deduplicate (exact Recall AI logic)
    const seenIds = new Set<string>();
    const memories: any[] = [];

    for (const m of filteredResults) {
      const id = m._id.toString();
      if (!seenIds.has(id)) {
        memories.push(m);
        seenIds.add(id);
      }
    }
    for (const m of unfilteredResults) {
      const id = m._id.toString();
      if (!seenIds.has(id)) {
        memories.push(m);
        seenIds.add(id);
      }
    }

    const finalMemories = memories.slice(0, Math.floor(K * 1.5));

    // 5. Generate Groq response (exact Recall AI logic)
    let responseText: string;
    let sources: string[];

    if (finalMemories.length > 0) {
      responseText = await generateRecallResponse(query, finalMemories);
      sources = finalMemories.map((m) => `[${m.type}] ${m.rawText}`);
    } else {
      responseText = "I couldn't find any relevant memories to answer that.";
      sources = [];
    }

    res.json({ response: responseText, sources });
  } catch (error) {
    logger.error("Error in recall chat:", error);
    res.status(500).json({ message: "Error processing recall query" });
  }
};
