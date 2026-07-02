import mongoose, { Document, Schema } from "mongoose";

export interface ICareAlert extends Document {
  patientId: mongoose.Types.ObjectId;
  caregiverIds: mongoose.Types.ObjectId[];
  type: string;
  title: string;
  body: string;
  metadata?: Record<string, unknown>;
  createdAt: Date;
  /** Per-caregiver read receipts */
  readBy?: { caregiverId: mongoose.Types.ObjectId; readAt: Date }[];
}

const ReadEntrySchema = new Schema(
  {
    caregiverId: { type: Schema.Types.ObjectId, ref: "User", required: true },
    readAt: { type: Date, required: true },
  },
  { _id: false }
);

const CareAlertSchema = new Schema<ICareAlert>(
  {
    patientId: {
      type: Schema.Types.ObjectId,
      ref: "User",
      required: true,
      index: true,
    },
    caregiverIds: [{ type: Schema.Types.ObjectId, ref: "User" }],
    type: { type: String, required: true, index: true },
    title: { type: String, required: true },
    body: { type: String, required: true },
    metadata: { type: Schema.Types.Mixed },
    readBy: { type: [ReadEntrySchema], default: [] },
  },
  { timestamps: { createdAt: true, updatedAt: false } }
);

export const CareAlert = mongoose.model<ICareAlert>("CareAlert", CareAlertSchema);
