import mongoose, { Document, Schema } from "mongoose";

export type CareTaskPriority = "HIGH" | "MEDIUM" | "LOW";
export type CareTaskStatus = "pending" | "done";

export interface ICareTask extends Document {
  patientId: mongoose.Types.ObjectId;
  caregiverId: mongoose.Types.ObjectId;
  title: string;
  description: string;
  priority: CareTaskPriority;
  dueAt?: Date;
  status: CareTaskStatus;
  doneAt?: Date;
  createdAt: Date;
  updatedAt: Date;
}

const CareTaskSchema = new Schema<ICareTask>(
  {
    patientId: { type: Schema.Types.ObjectId, ref: "User", required: true, index: true },
    caregiverId: { type: Schema.Types.ObjectId, ref: "User", required: true, index: true },
    title: { type: String, required: true, trim: true },
    description: { type: String, default: "" },
    priority: {
      type: String,
      enum: ["HIGH", "MEDIUM", "LOW"],
      default: "MEDIUM",
      index: true,
    },
    dueAt: { type: Date, required: false },
    status: {
      type: String,
      enum: ["pending", "done"],
      default: "pending",
      index: true,
    },
    doneAt: { type: Date, required: false },
  },
  { timestamps: true }
);

CareTaskSchema.index({ patientId: 1, status: 1, dueAt: 1 });

export const CareTask = mongoose.model<ICareTask>("CareTask", CareTaskSchema);
