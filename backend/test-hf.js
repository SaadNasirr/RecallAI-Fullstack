const axios = require('axios');
require('dotenv').config();

async function test() {
  try {
    const response = await axios.post(
      "https://router.huggingface.co/hf-inference/pipeline/feature-extraction/sentence-transformers/all-MiniLM-L6-v2",
      { inputs: ["this is a test sentence"] },
      { headers: { Authorization: `Bearer ${process.env.HF_TOKEN}` } }
    );
    console.log("Success! Array length:", response.data.length);
    if (response.data[0] && Array.isArray(response.data[0])) {
      console.log("Dims:", response.data[0].length);
    } else {
        console.log("Raw:", response.data.slice(0, 5));
    }
  } catch (e) {
    console.error("Error:", e.response ? e.response.data : e.message);
  }
}

test();
