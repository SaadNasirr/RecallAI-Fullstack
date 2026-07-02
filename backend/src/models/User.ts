import mongoose, { Document, Schema } from "mongoose";

export interface IUser extends Document {
  name: string;
  email: string;
  password: string;
  role: "patient" | "caregiver";
  gender?: "male" | "female";
  phone?: string;
  /** Last known position captured at signup (patient) */
  liveLat?: number;
  liveLng?: number;
  /** When liveLat/liveLng were last updated (e.g. patient device share) */
  liveLocationUpdatedAt?: Date;
  /** Approximate last client activity (login, key writes) */
  lastActiveAt?: Date;
  /** FCM device tokens (max managed in app logic) */
  fcmTokens?: { token: string; updatedAt: Date }[];
  profileImageUrl?: string;
}

const UserSchema = new Schema<IUser>(
  {
    name: { type: String, required: true },
    email: { type: String, required: true, unique: true },
    password: { type: String, required: true },
    role: { type: String, enum: ["patient", "caregiver"], required: true },
    gender: { type: String, enum: ["male", "female"], required: false },
    phone: { type: String, required: false },
    liveLat: { type: Number, required: false },
    liveLng: { type: Number, required: false },
    liveLocationUpdatedAt: { type: Date, required: false },
    lastActiveAt: { type: Date, required: false },
    profileImageUrl: { type: String, required: false },
    fcmTokens: {
      type: [
        {
          token: { type: String, required: true },
          updatedAt: { type: Date, default: Date.now },
        },
      ],
      default: [],
    },
  },
  { timestamps: true }
);

export const User = mongoose.model<IUser>("User", UserSchema);
