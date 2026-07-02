import mongoose, { Schema, Document, Types } from "mongoose";

export interface IFaceProfileEntry {
  clientId: string;
  name: string;
  embedding: number[];
  updatedAt?: Date;
}

export interface IFaceProfiles extends Document {
  userId: Types.ObjectId;
  descriptorSchemaVersion: number;
  profiles: IFaceProfileEntry[];
  updatedAt: Date;
  createdAt: Date;
}

const faceProfileEntrySchema = new Schema<IFaceProfileEntry>(
  {
    clientId: { type: String, required: true, trim: true },
    name: { type: String, required: true, trim: true },
    embedding: { type: [Number], required: true, default: [] },
    updatedAt: { type: Date, default: Date.now },
  },
  { _id: false }
);

const faceProfilesSchema = new Schema<IFaceProfiles>(
  {
    userId: {
      type: Schema.Types.ObjectId,
      ref: "User",
      required: true,
      unique: true,
    },
    descriptorSchemaVersion: { type: Number, default: 4 },
    profiles: {
      type: [faceProfileEntrySchema],
      default: [],
    },
  },
  { timestamps: true }
);

faceProfilesSchema.index({ userId: 1 }, { unique: true });

const FaceProfiles = mongoose.model<IFaceProfiles>(
  "FaceProfiles",
  faceProfilesSchema
);

export { FaceProfiles };
