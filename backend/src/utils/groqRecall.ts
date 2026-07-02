import Groq from "groq-sdk";
import { MEMORY_ANALYSIS_MODEL, RECALL_REASONING_MODEL } from "../config/modelConfig";
import { withTimeout } from "./asyncUtils";

// Native date helpers (avoids date-fns dependency)
function fmtDateTime(d: Date) {
  const pad = (n: number) => n.toString().padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}
function fmtDayName(d: Date) {
  return d.toLocaleDateString('en-US', { weekday: 'long' });
}

// Lazy Groq client — created on first use so dotenv.config() has already run
let _groq: Groq | null = null;
let _groqAuthFailed = false;
function readGroqKey(): string | null {
  const key = process.env.GROQ_API_KEY?.trim();
  return key ? key : null;
}
function getGroq(): Groq {
  if (!_groq) {
    _groq = new Groq({ apiKey: readGroqKey() || undefined });
  }
  return _groq;
}

function isInvalidGroqKey(err: any): boolean {
  const msg = String(err?.message || "");
  const code = String(err?.error?.error?.code || err?.error?.code || "");
  return (
    msg.includes("Invalid API Key") ||
    msg.includes("invalid_api_key") ||
    code === "invalid_api_key" ||
    (err?.status === 401)
  );
}

function fallbackRecallFromMemories(query: string, memories: RecallMemory[]): string {
  const q = query.toLowerCase();
  if (memories.length === 0) return "I couldn't find any relevant memories to answer that.";

  const top = memories[0];
  const topText = String(top.text || "");
  const userMatch =
    topText.match(/(?:^\[.*?\]\s*)?User(?:\s*\(voice\))?\s*:\s*(.+)$/im) ||
    topText.match(/Patient\s*:\s*(.+)$/im);
  const userSaid = (userMatch?.[1] || "").trim();
  const timeMatch = topText.match(/^\[(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2})\]/);
  const memoryTime = (timeMatch?.[1] || "").trim();

  if (q.includes("last time") || q.includes("what did i say")) {
    if (userSaid) return `You said: ${userSaid}`;
    return `Latest memory: ${topText.slice(0, 140)}${topText.length > 140 ? "..." : ""}`;
  }
  if (q.startsWith("when") || q.includes("when did") || q.includes("what time") || q.includes("what date")) {
    if (memoryTime && userSaid) return `You said "${userSaid}" on ${memoryTime}.`;
    if (memoryTime) return `That happened on ${memoryTime}.`;
  }
  return `Most relevant memory: ${String(memories[0].text || "").slice(0, 180)}${String(memories[0].text || "").length > 180 ? "..." : ""}`;
}

// ─── Local classification helpers (zero LLM calls, <1ms each) ────────────────

/**
 * Detects the memory_type a recall query is targeting using regex rules.
 * Same accuracy as the previous LLM approach (which used identical rules in prose).
 */
function detectRetrievalIntentLocal(query: string): string | null {
  const q = query.toLowerCase();

  // location
  if (/where (is|are|did i (put|leave|keep|place))/i.test(q)) return "location";
  if (/where('s| is) my/i.test(q)) return "location";
  if (/\b(i (put|left|placed|kept)) .+ (on|in|at|near)/i.test(q)) return "location";

  // habit / routine
  if (/(do i usually|how often|my routine|every (morning|evening|night|day|week)|i (always|usually|normally))/i.test(q)) return "habit";
  if (/(medicine|medication|pills?|tablet).*(time|when|morning|evening)/i.test(q)) return "habit";
  if (/when do i (take|eat|sleep|wake|exercise)/i.test(q)) return "habit";

  // event
  if (/(when did i|what did i do|did i (go|visit|meet|see|attend)|what happened)/i.test(q)) return "event";
  if (/\b(appointment|doctor|hospital|clinic)\b/i.test(q)) return "event";
  if (/(last|this|next) (monday|tuesday|wednesday|thursday|friday|saturday|sunday|week|month)/i.test(q)) return "event";

  // object
  if (/(what (does|did).+look like|describe my|what kind of)/i.test(q)) return "object";

  // conversation
  if (/(what did .+ say|what did we (talk|discuss)|told me|said to me)/i.test(q)) return "conversation";
  if (/(spoke with|talked to|called) .+ (about|and)/i.test(q)) return "conversation";

  return null;
}

/**
 * Replaces relative date words (today, yesterday, tomorrow, last/next weekday)
 * with YYYY-MM-DD strings. Deterministic math — more reliable than LLM for dates.
 */
function normalizeQueryLocal(text: string): string {
  const now = new Date();
  const pad = (n: number) => n.toString().padStart(2, "0");
  const fmt = (d: Date) =>
    `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;

  function getLastWeekday(targetDay: number): string {
    const d = new Date(now);
    const diff = (d.getDay() - targetDay + 7) % 7 || 7;
    d.setDate(d.getDate() - diff);
    return fmt(d);
  }
  function getNextWeekday(targetDay: number): string {
    const d = new Date(now);
    const diff = (targetDay - d.getDay() + 7) % 7 || 7;
    d.setDate(d.getDate() + diff);
    return fmt(d);
  }

  const today = fmt(now);
  const yesterday = fmt(new Date(now.getTime() - 86400000));
  const tomorrow = fmt(new Date(now.getTime() + 86400000));

  return text
    .replace(/\btoday\b/gi, today)
    .replace(/\byesterday\b/gi, yesterday)
    .replace(/\btomorrow\b/gi, tomorrow)
    .replace(/\blast monday\b/gi, getLastWeekday(1))
    .replace(/\blast tuesday\b/gi, getLastWeekday(2))
    .replace(/\blast wednesday\b/gi, getLastWeekday(3))
    .replace(/\blast thursday\b/gi, getLastWeekday(4))
    .replace(/\blast friday\b/gi, getLastWeekday(5))
    .replace(/\blast saturday\b/gi, getLastWeekday(6))
    .replace(/\blast sunday\b/gi, getLastWeekday(0))
    .replace(/\bnext monday\b/gi, getNextWeekday(1))
    .replace(/\bnext tuesday\b/gi, getNextWeekday(2))
    .replace(/\bnext wednesday\b/gi, getNextWeekday(3))
    .replace(/\bnext thursday\b/gi, getNextWeekday(4))
    .replace(/\bnext friday\b/gi, getNextWeekday(5))
    .replace(/\bnext saturday\b/gi, getNextWeekday(6))
    .replace(/\bnext sunday\b/gi, getNextWeekday(0))
    .replace(/\bnext week\b/gi, `week of ${fmt(new Date(now.getTime() + 7 * 86400000))}`)
    .replace(/\blast week\b/gi, `week of ${fmt(new Date(now.getTime() - 7 * 86400000))}`);
}

/**
 * Classifies memory type from text using keyword rules.
 * Covers ~60-70% of typical memory inputs. Returns null for ambiguous cases
 * which then fall through to the LLM.
 */
function classifyMemoryTypeLocal(text: string): string | null {
  const t = text.toLowerCase();

  if (/(where (is|are)|i (put|left|parked)|(keys|glasses|wallet|phone|bag|car) (is|are|on|in|at|near|under))/i.test(t)) return "location";
  if (/\b(parked|keys are|bag is|wallet is|glasses are|phone is|medicine is)\b/i.test(t)) return "location";

  if (/(every|always|usually|routine|each) .*(morning|evening|day|night|week)/i.test(t) &&
    /(take|eat|do|go|wake|sleep|exercise|walk|run)/i.test(t)) return "habit";
  if (/i (always|usually|normally) (take|eat|do|go|wake|sleep)/i.test(t)) return "habit";

  if (/i (saw|met|visited|went to|attended|had a meeting with|spoke with|called)\b/i.test(t)) return "event";
  if (/\b(appointment|checkup|check-up)\b/i.test(t) && !/every|always|routine/i.test(t)) return "event";

  if (/(my .+ is (red|blue|black|white|grey|gray|silver|gold|brown|green|small|large|big|old|new|broken|fixed))/i.test(t)) return "object";

  if (/(said|told me|said to me|asked me)/i.test(t) &&
    /(doctor|nurse|daughter|son|wife|husband|friend|sister|brother)/i.test(t)) return "conversation";
  if (/(spoke with|talked to|discussed with|called)/i.test(t) &&
    /(doctor|nurse|daughter|son|wife|husband|friend)/i.test(t)) return "conversation";

  return null;
}

/**
 * Extracts meaningful keywords from text without using an LLM.
 * Good enough for 80% of cases; LLM produces better tags for complex inputs.
 */
function extractTagsLocal(text: string): string[] {
  const stopwords = new Set([
    "i", "my", "me", "the", "a", "an", "is", "are", "was", "were", "be",
    "been", "am", "to", "of", "and", "or", "in", "on", "at", "for", "with",
    "have", "has", "had", "did", "do", "does", "went", "put", "left", "it",
    "this", "that", "they", "them", "their", "she", "he", "we", "you",
    "today", "yesterday", "tomorrow", "just", "now", "then", "some", "from",
    "by", "about", "but", "not", "so", "its", "when", "where", "what", "who",
    "how", "if", "also", "very", "will", "can", "could", "would", "should",
    "been", "there", "here", "one", "two", "into", "than", "more", "after",
  ]);
  return text
    .toLowerCase()
    .replace(/[^a-z\s]/g, " ")
    .split(/\s+/)
    .filter(w => w.length > 3 && !stopwords.has(w))
    .slice(0, 3);
}

/**
 * Returns true when the text contains time expressions complex enough
 * to need LLM parsing (specific hours, named months, last/next weekday).
 */
function hasComplexTimeExpression(text: string): boolean {
  return (
    /\b(next|last) (monday|tuesday|wednesday|thursday|friday|saturday|sunday|week|month|year)\b/i.test(text) ||
    /\bat \d{1,2}(:\d{2})?\s*(am|pm)\b/i.test(text) ||
    /\b\d{1,2}:\d{2}\s*(am|pm)?\b/i.test(text) ||
    /\b(january|february|march|april|may|june|july|august|september|october|november|december)\b/i.test(text)
  );
}

// ─── Types ────────────────────────────────────────────────────────────────────

export interface MemoryAnalysis {
  normalized_text: string;
  event_time: string | null;
  memory_type: "event" | "habit" | "location" | "object" | "conversation" | "note";
  tags: string[];
}

export interface RecallMemory {
  text: string;
  raw_text: string;
  type: string;
}

// ─── Exported functions ───────────────────────────────────────────────────────

/**
 * Analyze memory content.
 * Simple unambiguous memories are classified locally (zero LLM calls).
 * Complex memories with ambiguous types or specific times go to Groq.
 */
export async function analyzeMemoryContent(text: string): Promise<MemoryAnalysis> {
  const currentTime = new Date();
  const formattedTime = fmtDateTime(currentTime);
  const dayName = fmtDayName(currentTime);

  // Fast path: local classification covers ~60-70% of typical memories
  const localType = classifyMemoryTypeLocal(text);
  if (localType && !hasComplexTimeExpression(text)) {
    return {
      normalized_text: normalizeQueryLocal(text),
      event_time: localType === "habit" ? null : formattedTime,
      memory_type: localType as MemoryAnalysis["memory_type"],
      tags: extractTagsLocal(text),
    };
  }

  // Slow path: LLM for complex or ambiguous memories
  const pad = (n: number) => n.toString().padStart(2, "0");
  const d = new Date(currentTime.getTime() - 86400000);
  const yesterdayDate = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;

  const systemPrompt = `Current date and time: ${formattedTime} (${dayName})

Extract metadata from the user's memory note. Return ONLY valid JSON, no markdown, no backticks.

Fields:
- "normalized_text": Rewrite replacing relative times (today, yesterday, tomorrow) with actual YYYY-MM-DD dates. Keep everything else unchanged.
- "event_time": If a specific time/date is mentioned or implied, use "YYYY-MM-DD HH:MM:SS" format. Use "${formattedTime}" when user says "now", "just now", or "today" with no specific time. Use null only for recurring habits with no fixed time (e.g. "I run every morning").
- "memory_type": Choose exactly ONE:
    "event" — A specific thing that happened or will happen. DEFAULT when unsure.
    "habit" — A recurring action.
    "location" — Where something is.
    "object" — Describes an item.
    "conversation" — A chat with a person.
    "note" — General information with no better fit.
- "tags": 1-3 lowercase keywords most useful for finding this memory later.

Examples:
Input: "I parked under the big tree yesterday"
Output: {"normalized_text":"On ${yesterdayDate} I parked under the big tree","event_time":"${yesterdayDate} 12:00:00","memory_type":"location","tags":["parking","car","tree"]}

Input: "I run 5km every Sunday morning"
Output: {"normalized_text":"I run 5km every Sunday morning","event_time":null,"memory_type":"habit","tags":["running","exercise","sunday"]}

Input: "Today I had a meeting with Sara"
Output: {"normalized_text":"On ${formattedTime.slice(0, 10)} I had a meeting with Sara","event_time":"${formattedTime}","memory_type":"conversation","tags":["meeting","sara"]}`;

  try {
    if (!readGroqKey() || _groqAuthFailed) {
      return {
        normalized_text: normalizeQueryLocal(text),
        event_time: formattedTime,
        memory_type: (localType as MemoryAnalysis["memory_type"]) || "note",
        tags: extractTagsLocal(text),
      };
    }

    const completion = await getGroq().chat.completions.create({
      messages: [
        { role: "system", content: systemPrompt },
        { role: "user", content: text },
      ],
      model: MEMORY_ANALYSIS_MODEL,
      temperature: 0.1,
      response_format: { type: "json_object" },
    });

    const result = completion.choices[0].message.content || "{}";
    return JSON.parse(result) as MemoryAnalysis;
  } catch (error) {
    if (isInvalidGroqKey(error)) {
      _groqAuthFailed = true;
      console.warn("Groq auth failed in memory analysis; using local fallback.");
    } else {
      console.error("Error in memory analysis:", error);
    }
    return {
      normalized_text: normalizeQueryLocal(text),
      event_time: null,
      memory_type: (localType as MemoryAnalysis["memory_type"]) || "note",
      tags: extractTagsLocal(text),
    };
  }
}

/**
 * Detect the memory_type a recall query is looking for.
 * Pure regex — replaces the previous LLM call (~300 tokens saved per recall query).
 * Returns actual null (not the string "null") so the caller's truthiness check works correctly.
 */
export async function detectRetrievalIntent(query: string): Promise<string | null> {
  return detectRetrievalIntentLocal(query);
}

/**
 * Normalize time-sensitive queries by replacing relative date words with YYYY-MM-DD dates.
 * Pure date math — replaces the previous LLM call (~250 tokens saved per time-sensitive recall).
 * More reliable than LLM for date arithmetic.
 */
export async function normalizeQueryWithLLM(text: string): Promise<string> {
  return normalizeQueryLocal(text);
}

/**
 * Generate a Groq chat response from retrieved memories.
 */
export async function generateRecallResponse(
  query: string,
  memories: RecallMemory[]
): Promise<string> {
  const apiKey = readGroqKey();
  if (!apiKey || _groqAuthFailed) {
    return fallbackRecallFromMemories(query, memories);
  }

  const memoryContext = memories.map((m) => `- ${m.text}`).join("\n");
  const currentTimeStr = `${fmtDateTime(new Date())} (${fmtDayName(new Date())})`;

  const systemPrompt = `You are Recall, a warm memory assistant for someone with memory difficulties.
Current time: ${currentTimeStr}

Your job: answer the user's question using ONLY the memories listed below. Do not invent facts. Do not use outside knowledge.

Response rules:
- Write in plain, simple language. Short sentences. No jargon.
- Maximum 4 sentences per response.
- Do not use bullet points unless there are 3 or more distinct items to report.
- If the user asks about a specific date ("today", "Monday", "yesterday"), only use memories from that exact date. If no memory exists for that date, say: "I don't have anything saved for [date]. Would you like to add a note about it?"
- If a memory is partial (missing a time or place), share what you know and say what is missing.
- Never say "based on the information provided" or "according to the memories" — just answer naturally.
- If no memory is relevant, say: "I couldn't find anything about that. You can save it and I'll remember it next time."`;

  try {
    const completion = await withTimeout(
      getGroq().chat.completions.create({
        messages: [
          { role: "system", content: systemPrompt },
          {
            role: "user",
            content: `User Question: ${query}\nRelevant Memories:\n${memoryContext}`,
          },
        ],
        model: RECALL_REASONING_MODEL,
        temperature: 0.7,
      }),
      90_000,
      "Groq recall chat completion"
    );

    return completion.choices[0].message.content || "I couldn't generate a response.";
  } catch (error: any) {
    const msg = String(error?.message || "");
    if (msg.includes("timed out")) {
      return fallbackRecallFromMemories(query, memories);
    }
    if (isInvalidGroqKey(error)) {
      _groqAuthFailed = true;
      return fallbackRecallFromMemories(query, memories);
    }
    return `Error generating response: ${error.message}`;
  }
}
