import mongoose, { Document, Schema } from "mongoose";

export type CareRelationshipStatus = "pending" | "approved" | "rejected" | "blocked";
export type CareInviteMethod = "qr" | "code";

export interface CarePermissions {
  viewMemories: boolean;
  manageReminders: boolean;
  receiveAlerts: boolean;
  viewLocation: boolean;
  emergencyAccess: boolean;
}

export interface ICareRelationship extends Document {
  patientId: mongoose.Types.ObjectId;
  caregiverId: mongoose.Types.ObjectId;
  status: CareRelationshipStatus;
  permissions: CarePermissions;
  relationshipType: "primary" | "secondary";
  inviteMethod: CareInviteMethod;
  createdAt: Date;
  updatedAt: Date;
}

const PermissionsSchema = new Schema<CarePermissions>(
  {
    viewMemories: { type: Boolean, default: true },
    manageReminders: { type: Boolean, default: true },
    receiveAlerts: { type: Boolean, default: true },
    viewLocation: { type: Boolean, default: true },
    emergencyAccess: { type: Boolean, default: true },
  },
  { _id: false }
);

const CareRelationshipSchema = new Schema<ICareRelationship>(
  {
    patientId: {
      type: Schema.Types.ObjectId,
      ref: "User",
      required: true,
      index: true,
    },
    caregiverId: {
      type: Schema.Types.ObjectId,
      ref: "User",
      required: true,
      index: true,
    },
    status: {
      type: String,
      enum: ["pending", "approved", "rejected", "blocked"],
      default: "pending",
      index: true,
    },
    permissions: { type: PermissionsSchema, required: true },
    relationshipType: {
      type: String,
      enum: ["primary", "secondary"],
      default: "secondary",
    },
    inviteMethod: {
      type: String,
      enum: ["qr", "code"],
      required: true,
    },
  },
  { timestamps: true }
);

CareRelationshipSchema.index(
  { patientId: 1, caregiverId: 1 },
  { unique: true }
);

export const CareRelationship = mongoose.model<ICareRelationship>(
  "CareRelationship",
  CareRelationshipSchema
);
