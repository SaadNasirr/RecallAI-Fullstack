import mongoose, { Document, Schema } from "mongoose";
import type { CareInviteMethod } from "./CareRelationship";

export interface ICareInvite extends Document {
  patientId: mongoose.Types.ObjectId;
  tokenHash: string;
  shortCode?: string;
  expiresAt: Date;
  consumedAt?: Date;
  method: CareInviteMethod;
  createdAt: Date;
}

const CareInviteSchema = new Schema<ICareInvite>(
  {
    patientId: {
      type: Schema.Types.ObjectId,
      ref: "User",
      required: true,
      index: true,
    },
    tokenHash: { type: String, required: true, index: true },
    shortCode: { type: String, sparse: true, unique: true, uppercase: true },
    expiresAt: { type: Date, required: true, index: true },
    consumedAt: { type: Date },
    method: { type: String, enum: ["qr", "code"], required: true },
  },
  { timestamps: true }
);

CareInviteSchema.index({ shortCode: 1 }, { sparse: true, unique: true });

export const CareInvite = mongoose.model<ICareInvite>("CareInvite", CareInviteSchema);
