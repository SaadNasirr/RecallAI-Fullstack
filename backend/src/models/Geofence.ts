import mongoose, { Document, Schema } from "mongoose";

export interface IGeofence extends Document {
  patientId: mongoose.Types.ObjectId;
  caregiverId: mongoose.Types.ObjectId;
  name: string;
  centerLat: number;
  centerLng: number;
  radiusMeters: number;
  color: string;
  isActive: boolean;
  createdAt: Date;
  updatedAt: Date;
}

const GeofenceSchema = new Schema<IGeofence>(
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
    name: { type: String, required: true, trim: true },
    centerLat: { type: Number, required: true },
    centerLng: { type: Number, required: true },
    radiusMeters: { type: Number, required: true, min: 1 },
    color: { type: String, required: true, default: "#1E88E5" },
    isActive: { type: Boolean, default: true },
  },
  { timestamps: true }
);

GeofenceSchema.index({ patientId: 1, caregiverId: 1 });

export const Geofence = mongoose.model<IGeofence>("Geofence", GeofenceSchema);
