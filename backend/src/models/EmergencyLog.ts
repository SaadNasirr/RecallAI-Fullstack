import mongoose, { Document, Schema } from "mongoose";

export interface IEmergencyLog extends Document {
  patientId: mongoose.Types.ObjectId;
  type: string;
  message?: string;
  lat?: number;
  lng?: number;
  metadata?: Record<string, unknown>;
  createdAt: Date;
}

const EmergencyLogSchema = new Schema<IEmergencyLog>(
  {
    patientId: { type: Schema.Types.ObjectId, ref: "User", required: true, index: true },
    type: { type: String, required: true, index: true },
    message: { type: String },
    lat: { type: Number },
    lng: { type: Number },
    metadata: { type: Schema.Types.Mixed },
  },
  { timestamps: { createdAt: true, updatedAt: false } }
);

EmergencyLogSchema.index({ patientId: 1, createdAt: -1 });

export const EmergencyLog = mongoose.model<IEmergencyLog>("EmergencyLog", EmergencyLogSchema);
