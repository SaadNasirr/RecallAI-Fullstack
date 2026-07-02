import mongoose, { Document, Schema } from "mongoose";

export interface IMemory extends Document {
  userId: mongoose.Types.ObjectId;
  text: string;          // timestamped text: "[2024-01-01] walked to park"
  rawText: string;       // normalized text without timestamp
  originalInput: string; // what user originally typed
  timestamp: string;     // when stored
  eventTime: string;     // extracted event time (may differ from timestamp)
  type: "event" | "habit" | "location" | "object" | "conversation" | "note";
  tags: string[];
  embedding: number[];   // 384-dim vector from all-MiniLM-L6-v2
  createdAt: Date;
}

const MemorySchema = new Schema<IMemory>(
  {
    userId: { type: Schema.Types.ObjectId, ref: "User", required: true, index: true },
    text: { type: String, required: true },
    rawText: { type: String, required: true },
    originalInput: { type: String, required: true },
    timestamp: { type: String, required: true },
    eventTime: { type: String, default: "" },
    type: {
      type: String,
      enum: ["event", "habit", "location", "object", "conversation", "note"],
      default: "note",
    },
    tags: [{ type: String }],
    embedding: [{ type: Number }],
  },
  { timestamps: true }
);

// Compound indexes for the two common query patterns:
// 1. getMemories: find by userId, sort by createdAt desc
// 2. addMemory dedup check: find by userId + originalInput + createdAt
MemorySchema.index({ userId: 1, createdAt: -1 });
MemorySchema.index({ userId: 1, originalInput: 1 });

export const Memory = mongoose.model<IMemory>("Memory", MemorySchema);
