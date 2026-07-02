import mongoose, { Document, Schema } from "mongoose";

export type GeofenceEventType = "entered" | "exited";

export interface IGeofenceEvent extends Document {
  geofenceId: mongoose.Types.ObjectId;
  patientId: mongoose.Types.ObjectId;
  eventType: GeofenceEventType;
  triggeredAt: Date;
  location: { lat: number; lng: number };
  createdAt: Date;
  updatedAt: Date;
}

const GeofenceEventSchema = new Schema<IGeofenceEvent>(
  {
    geofenceId: {
      type: Schema.Types.ObjectId,
      ref: "Geofence",
      required: true,
      index: true,
    },
    patientId: {
      type: Schema.Types.ObjectId,
      ref: "User",
      required: true,
      index: true,
    },
    eventType: { type: String, enum: ["entered", "exited"], required: true },
    triggeredAt: { type: Date, required: true, default: () => new Date() },
    location: {
      lat: { type: Number, required: true },
      lng: { type: Number, required: true },
    },
  },
  { timestamps: true }
);

GeofenceEventSchema.index({ patientId: 1, triggeredAt: -1 });

export const GeofenceEvent = mongoose.model<IGeofenceEvent>(
  "GeofenceEvent",
  GeofenceEventSchema
);
