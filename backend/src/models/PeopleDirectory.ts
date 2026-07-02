import mongoose, { Schema, Document, Types } from "mongoose";

export interface IPersonEntry {
  clientId: string;
  name: string;
  relation?: string;
  note?: string;
  phone?: string;
  updatedAt?: Date;
}

export interface IPeopleDirectory extends Document {
  userId: Types.ObjectId;
  people: IPersonEntry[];
  updatedAt: Date;
  createdAt: Date;
}

const personEntrySchema = new Schema<IPersonEntry>(
  {
    clientId: { type: String, required: true, trim: true },
    name: { type: String, required: true, trim: true },
    relation: { type: String, default: "", trim: true },
    note: { type: String, default: "", trim: true },
    phone: { type: String, default: "", trim: true },
    updatedAt: { type: Date, default: Date.now },
  },
  { _id: false }
);

const peopleDirectorySchema = new Schema<IPeopleDirectory>(
  {
    userId: {
      type: Schema.Types.ObjectId,
      ref: "User",
      required: true,
      unique: true,
    },
    people: {
      type: [personEntrySchema],
      default: [],
    },
  },
  { timestamps: true }
);

peopleDirectorySchema.index({ userId: 1 }, { unique: true });

const PeopleDirectory = mongoose.model<IPeopleDirectory>(
  "PeopleDirectory",
  peopleDirectorySchema
);

export { PeopleDirectory };
