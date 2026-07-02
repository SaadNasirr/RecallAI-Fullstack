import { HfInference } from "@huggingface/inference";

let _hf: HfInference | null = null;

/** Exported for timeout fallback paths (same deterministic vec as generateEmbedding catch). */
export function localFallbackEmbedding(text: string, dim: number = 384): number[] {
  // Deterministic lightweight fallback so memory save never fails.
  const vec = new Array<number>(dim).fill(0);
  const source = (text || "").trim().toLowerCase();
  for (let i = 0; i < source.length; i++) {
    const code = source.charCodeAt(i);
    const idx = (code * 31 + i * 17) % dim;
    vec[idx] += 1;
  }
  const norm = Math.sqrt(vec.reduce((acc, v) => acc + v * v, 0));
  if (norm > 0) {
    for (let i = 0; i < vec.length; i++) vec[i] = vec[i] / norm;
  }
  return vec;
}

function getHfClient(): HfInference {
  if (!_hf) {
    const token = process.env.HF_TOKEN;
    if (!token) throw new Error("HF_TOKEN is missing in .env");
    _hf = new HfInference(token);
  }
  return _hf;
}

/**
 * Generate a 384-dim embedding for text using HuggingFace Inference API
 * Uses the official JS client to ensure correct payload formatting
 */
export async function generateEmbedding(text: string): Promise<number[]> {
  try {
    const output = await getHfClient().featureExtraction({
      model: "sentence-transformers/all-MiniLM-L6-v2",
      inputs: text,
    });

    // output is typically (number | number[])[] from the pipeline
    if (Array.isArray(output) && Array.isArray(output[0])) {
      return output[0] as number[];
    }
    return output as number[];
  } catch (error: any) {
    console.warn(
      `HF embedding failed, using local fallback embedding. Reason: ${error?.message || "unknown"}`
    );
    return localFallbackEmbedding(text);
  }
}



/**
 * Cosine similarity between two vectors
 */
function cosineSimilarity(a: number[], b: number[]): number {
  if (a.length !== b.length) return 0;
  let dot = 0,
    normA = 0,
    normB = 0;
  for (let i = 0; i < a.length; i++) {
    dot += a[i] * b[i];
    normA += a[i] * a[i];
    normB += b[i] * b[i];
  }
  const denom = Math.sqrt(normA) * Math.sqrt(normB);
  return denom === 0 ? 0 : dot / denom;
}

export interface VectorSearchResult {
  id: string;
  score: number;
  [key: string]: any;
}

/**
 * Search memories by vector similarity (like FAISS IndexFlatL2 but using cosine similarity)
 * Takes pre-loaded items with their embedding arrays and ranks them
 */
export function vectorSearch(
  queryEmbedding: number[],
  items: Array<{ id: string; embedding: number[]; [key: string]: any }>,
  topK: number = 5,
  typeFilter?: string
): VectorSearchResult[] {
  // Apply filter first
  let candidates = typeFilter
    ? items.filter((item) => item.type === typeFilter)
    : items;

  // Compute similarities
  const scored = candidates.map((item) => ({
    ...item,
    score: cosineSimilarity(queryEmbedding, item.embedding),
  }));

  // Sort by score descending, return top K
  scored.sort((a, b) => b.score - a.score);
  return scored.slice(0, topK);
}
