require("dotenv").config({ override: true });
const { HfInference } = require("@huggingface/inference");

async function main() {
  const token = (process.env.HF_TOKEN || "").trim();
  if (!token) {
    console.error("HF_TOKEN is missing in .env");
    process.exit(1);
  }

  try {
    const hf = new HfInference(token);
    const output = await hf.featureExtraction({
      model: "sentence-transformers/all-MiniLM-L6-v2",
      inputs: "recallai hf token check",
    });
    const dim = Array.isArray(output?.[0]) ? output[0].length : output?.length;
    console.log(`HF token is valid. Embedding dimension: ${dim}`);
  } catch (error) {
    console.error(`HF token check failed: ${error?.message || error}`);
    process.exit(1);
  }
}

main();
