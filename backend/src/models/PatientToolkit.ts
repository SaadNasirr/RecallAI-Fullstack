import mongoose, { Schema, Document, Types } from "mongoose";

export interface IMedicationEntry {
  clientId: string;
  name: string;
  timeLabel: string;
  notes?: string;
  takenToday?: boolean;
  takenAt?: number | null;
  snoozeCount?: number;
  skippedToday?: boolean;
  skipReason?: string;
  adherenceStatus?: string;
  lastResetDate?: string;
  updatedAt?: Date;
}

export interface IRoutineEntry {
  clientId: string;
  title: string;
  period?: string;
  frequency?: string;
  timeLabel?: string;
  doneToday?: boolean;
  streakDays?: number;
  lastCompletedDate?: string;
  updatedAt?: Date;
}

export interface IConsentEntry {
  shareWithCaregiver?: boolean;
  allowLocationSharing?: boolean;
  allowVoiceStorage?: boolean;
  allowPhotoStorage?: boolean;
  updatedAt?: Date;
}

export interface IReminderEntry {
  clientId: string;
  title: string;
  description?: string;
  datetime: number;
  status?: string;
  source?: string;
  createdAt?: number;
  updatedAt?: number;
  warn10Min?: boolean;
  preset?: string;
  repeatMode?: string;
  daysOfWeekMask?: number;
}

export interface IAlarmEntry {
  clientId: string;
  label: string;
  hour: number;
  minute: number;
  repeatMode?: string;
  daysOfWeekMask?: number;
  enabled?: boolean;
  nextTriggerAt: number;
  createdAt?: number;
  updatedAt?: number;
}

export interface IPatientToolkit extends Document {
  userId: Types.ObjectId;
  medications: IMedicationEntry[];
  routines: IRoutineEntry[];
  consent: IConsentEntry;
  reminders: IReminderEntry[];
  alarms: IAlarmEntry[];
  updatedAt: Date;
  createdAt: Date;
}

const medicationEntrySchema = new Schema<IMedicationEntry>(
  {
    clientId: { type: String, required: true, trim: true },
    name: { type: String, required: true, trim: true },
    timeLabel: { type: String, default: "", trim: true },
    notes: { type: String, default: "", trim: true },
    takenToday: { type: Boolean, default: false },
    takenAt: { type: Number, default: null },
    snoozeCount: { type: Number, default: 0 },
    skippedToday: { type: Boolean, default: false },
    skipReason: { type: String, default: "", trim: true },
    adherenceStatus: { type: String, default: "PENDING", trim: true },
    lastResetDate: { type: String, default: "", trim: true },
    updatedAt: { type: Date, default: Date.now },
  },
  { _id: false }
);

const routineEntrySchema = new Schema<IRoutineEntry>(
  {
    clientId: { type: String, required: true, trim: true },
    title: { type: String, required: true, trim: true },
    period: { type: String, default: "Morning", trim: true },
    frequency: { type: String, default: "Daily", trim: true },
    timeLabel: { type: String, default: "", trim: true },
    doneToday: { type: Boolean, default: false },
    streakDays: { type: Number, default: 0 },
    lastCompletedDate: { type: String, default: "", trim: true },
    updatedAt: { type: Date, default: Date.now },
  },
  { _id: false }
);

const consentEntrySchema = new Schema<IConsentEntry>(
  {
    shareWithCaregiver: { type: Boolean, default: true },
    allowLocationSharing: { type: Boolean, default: true },
    allowVoiceStorage: { type: Boolean, default: true },
    allowPhotoStorage: { type: Boolean, default: true },
    updatedAt: { type: Date, default: Date.now },
  },
  { _id: false }
);

const reminderEntrySchema = new Schema<IReminderEntry>(
  {
    clientId: { type: String, required: true, trim: true },
    title: { type: String, required: true, trim: true },
    description: { type: String, default: "", trim: true },
    datetime: { type: Number, required: true },
    status: { type: String, default: "PENDING", trim: true },
    source: { type: String, default: "patient", trim: true },
    createdAt: { type: Number, default: () => Date.now() },
    updatedAt: { type: Number, default: () => Date.now() },
    warn10Min: { type: Boolean, default: true },
    preset: { type: String, default: "", trim: true },
    repeatMode: { type: String, default: "NONE", trim: true },
    daysOfWeekMask: { type: Number, default: 0 },
  },
  { _id: false }
);

const alarmEntrySchema = new Schema<IAlarmEntry>(
  {
    clientId: { type: String, required: true, trim: true },
    label: { type: String, required: true, trim: true },
    hour: { type: Number, required: true },
    minute: { type: Number, required: true },
    repeatMode: { type: String, default: "ONCE", trim: true },
    daysOfWeekMask: { type: Number, default: 0 },
    enabled: { type: Boolean, default: true },
    nextTriggerAt: { type: Number, required: true },
    createdAt: { type: Number, default: () => Date.now() },
    updatedAt: { type: Number, default: () => Date.now() },
  },
  { _id: false }
);

const patientToolkitSchema = new Schema<IPatientToolkit>(
  {
    userId: {
      type: Schema.Types.ObjectId,
      ref: "User",
      required: true,
      unique: true,
    },
    medications: { type: [medicationEntrySchema], default: [] },
    routines: { type: [routineEntrySchema], default: [] },
    consent: { type: consentEntrySchema, default: () => ({}) },
    reminders: { type: [reminderEntrySchema], default: [] },
    alarms: { type: [alarmEntrySchema], default: [] },
  },
  { timestamps: true }
);

patientToolkitSchema.index({ userId: 1 }, { unique: true });

const PatientToolkit = mongoose.model<IPatientToolkit>(
  "PatientToolkit",
  patientToolkitSchema
);

export { PatientToolkit };
