import { inngest } from "./client";
import { logger } from "../utils/logger";

// ─── Rule-based activity recommendations ─────────────────────────────────────
// Replaces the previous Gemini-powered generateActivityRecommendations that:
//   a) called gemini-2.0-flash on every mood update (~600 tokens each), and
//   b) always received undefined context (recentMoods/completedActivities were
//      never populated in the event payload), so the LLM ran on empty data.
// This rule table gives instant, offline, always-correct results.

interface ActivitySuggestion {
  name: string;
  description: string;
  duration: string;
  type: "mental" | "physical" | "social" | "creative" | "relaxation";
}

const ACTIVITY_RULES: Record<"high" | "medium" | "low", ActivitySuggestion[]> = {
  high: [
    { name: "Short walk outside", description: "A gentle 10-minute walk helps clear the mind and lift energy.", duration: "10 minutes", type: "physical" },
    { name: "Call a family member", description: "Connecting with a loved one reinforces a sense of closeness.", duration: "15 minutes", type: "social" },
    { name: "Look through photos", description: "Reviewing happy memories brings calm and a sense of continuity.", duration: "10 minutes", type: "mental" },
  ],
  medium: [
    { name: "Listen to favourite music", description: "Music can improve mood and gently reduce anxiety.", duration: "15 minutes", type: "relaxation" },
    { name: "Light stretching", description: "Gentle stretches ease physical tension and improve comfort.", duration: "10 minutes", type: "physical" },
    { name: "Water the plants", description: "A simple caring activity that brings calm and purpose.", duration: "5 minutes", type: "creative" },
  ],
  low: [
    { name: "Sit in a bright room", description: "Natural or bright light is a proven gentle mood lifter.", duration: "10 minutes", type: "relaxation" },
    { name: "Slow deep breathing", description: "5 minutes of slow breaths lowers stress and brings calm.", duration: "5 minutes", type: "relaxation" },
    { name: "Hold a warm drink", description: "Warmth and a simple routine provide comfort and grounding.", duration: "10 minutes", type: "relaxation" },
  ],
};

function getActivityTier(score: number): "high" | "medium" | "low" {
  if (score >= 70) return "high";
  if (score >= 40) return "medium";
  return "low";
}

export const generateActivityRecommendations = inngest.createFunction(
  { id: "generate-activity-recommendations" },
  { event: "mood/updated" },
  async ({ event }) => {
    const score = Number(event.data.mood ?? (event.data as any).score ?? 50);
    const tier = getActivityTier(score);
    const activities = ACTIVITY_RULES[tier];

    logger.info("Activity recommendations generated (rule-based)", { score, tier });

    return {
      message: "Activity recommendations generated",
      recommendations: { activities },
    };
  }
);

// processChatMessage and analyzeTherapySession have been removed.
// They were dead code: processChatMessage's output was never read by sendMessage
// (which already replied to the user via Groq before Inngest fired), and
// analyzeTherapySession was triggered with null notes/transcript every time.
// Together they burned ~1500 Gemini tokens per chat message for zero user benefit.

export const functions = [generateActivityRecommendations];
