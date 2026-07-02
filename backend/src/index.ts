import express from "express";
import cors from "cors";
import helmet from "helmet";
import morgan from "morgan";
import dotenv from "dotenv";
import { serve } from "inngest/express";
import { errorHandler } from "./middleware/errorHandler";
import { logger } from "./utils/logger";
import authRouter from "./routes/auth";
import chatRouter from "./routes/chat";
import moodRouter from "./routes/mood";
import activityRouter from "./routes/activity";
import memoryRouter from "./routes/memory";
import recallRouter from "./routes/recall";
import locatorRouter from "./routes/locator.routes";
import voiceRouter from "./routes/voice.routes";
import faceRouter from "./routes/face.routes";
import careRouter from "./routes/care.routes";
import geofenceRouter from "./routes/geofence";
import peopleDirectoryRouter from "./routes/peopleDirectory.routes";
import faceProfilesRouter from "./routes/faceProfiles.routes";
import patientToolkitRouter from "./routes/patientToolkit.routes";
import { connectDB } from "./utils/db";
import { inngest } from "./inngest/client";
import { functions as inngestFunctions } from "./inngest/functions";
import { modelConfig } from "./config/modelConfig";

// Load environment variables
dotenv.config({ override: true });

const groqKey = process.env.GROQ_API_KEY?.trim();
if (!groqKey) {
  logger.warn("GROQ_API_KEY is missing. Groq-powered features will use fallback behavior.");
} else if (!groqKey.startsWith("gsk_")) {
  logger.warn("GROQ_API_KEY format looks invalid (expected prefix: gsk_).");
}

// Create Express app
const app = express();

const corsOrigins = (process.env.CORS_ORIGINS || "")
  .split(",")
  .map((v) => v.trim())
  .filter(Boolean);
const allowAllCors = corsOrigins.length === 0;

// Middleware
app.use(helmet()); // Security headers
app.use(
  cors({
    origin: allowAllCors ? true : corsOrigins,
    credentials: true,
    methods: ["GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"],
    allowedHeaders: ["Content-Type", "Authorization"],
  })
); // Enable CORS
app.use(express.json()); // Parse JSON bodies
app.use(morgan("dev")); // HTTP request logger

// Set up Inngest endpoint
app.use(
  "/api/inngest",
  serve({ client: inngest, functions: inngestFunctions })
);
// OnaF6EGHhgYY9OPv

// Routes
app.get("/health", (req, res) => {
  res.json({ status: "ok", message: "Server is running" });
});

app.get("/health/models", (req, res) => {
  res.json({
    status: "ok",
    models: modelConfig,
    endpoints: {
      object: "/api/locator",
      voiceStt: "/api/voice/stt",
      voiceTts: "/api/voice/tts",
      geofenceActivity: "/api/activity",
      geofenceMood: "/api/mood",
      faceAnalyze: "/api/face",
      faceRecognize: "/api/face/recognize",
      faceEnroll: "/api/face/enroll",
      recall: "/recall/chat",
      peopleDirectory: "/people-directory",
      faceProfiles: "/face-profiles",
      patientToolkit: "/patient-toolkit",
    },
  });
});

app.use("/auth", authRouter);
app.use("/chat", chatRouter);
app.use("/api/mood", moodRouter);
app.use("/api/activity", activityRouter);
app.use("/memory", memoryRouter);
app.use("/recall", recallRouter);
app.use("/api/locator", locatorRouter);
app.use("/api/voice", voiceRouter);
app.use("/api/face", faceRouter);
app.use("/care", careRouter);
app.use("/geofence", geofenceRouter);
app.use("/people-directory", peopleDirectoryRouter);
app.use("/face-profiles", faceProfilesRouter);
app.use("/patient-toolkit", patientToolkitRouter);

// Error handling middleware
app.use(errorHandler);

// Start server
const startServer = async () => {
  try {
    // Connect to MongoDB first
    await connectDB();

    // Then start the server
    const PORT = process.env.PORT || 3001;
    app.listen(PORT, () => {
      logger.info(`Server is running on port ${PORT}`);
      logger.info(
        `Inngest endpoint available at http://localhost:${PORT}/api/inngest`
      );
    });
  } catch (error) {
    logger.error("Failed to start server:", error);
    process.exit(1);
  }
};

startServer();
