import { Types } from "mongoose";
import { CareTask, ICareTask } from "../models/CareTask";
import { User } from "../models/User";
import * as careAccess from "./careAccessService";
import { dispatchPatientAlert } from "./careService";
import { sendPushToUsers } from "./fcmService";

const PRI_ORDER: Record<string, number> = { HIGH: 0, MEDIUM: 1, LOW: 2 };

export async function createCareTask(
  caregiverId: string,
  patientId: string,
  input: {
    title: string;
    description?: string;
    priority?: "HIGH" | "MEDIUM" | "LOW";
    dueAt?: string | null;
  }
): Promise<ICareTask> {
  const ok = await careAccess.canManageReminders(caregiverId, patientId);
  if (!ok) {
    const err = new Error("FORBIDDEN") as Error & { status?: number };
    err.status = 403;
    throw err;
  }
  const title = String(input.title || "").trim();
  if (!title) {
    const err = new Error("TITLE_REQUIRED") as Error & { status?: number };
    err.status = 400;
    throw err;
  }
  const pr = String(input.priority || "MEDIUM").toUpperCase();
  const priority = (["HIGH", "MEDIUM", "LOW"].includes(pr) ? pr : "MEDIUM") as
    | "HIGH"
    | "MEDIUM"
    | "LOW";
  let due: Date | undefined;
  if (input.dueAt) {
    const d = new Date(String(input.dueAt));
    if (!Number.isNaN(d.getTime())) due = d;
  }
  const cg = await User.findById(caregiverId).select("name").lean();
  const caregiverName = String((cg as { name?: string })?.name || "").trim() || "Caregiver";
  const task = await CareTask.create({
    patientId: new Types.ObjectId(patientId),
    caregiverId: new Types.ObjectId(caregiverId),
    title,
    description: String(input.description || "").trim(),
    priority,
    dueAt: due,
    status: "pending",
  });
  const dueLabel = due ? due.toLocaleString("en-US") : "unspecified";
  const body = `${caregiverName} assigned you a task: ${title} at ${dueLabel} — Priority: ${priority}`;
  await sendPushToUsers(
    [patientId],
    { title: "New care task", body },
    {
      type: "care_task",
      taskId: String(task._id),
      patientId,
      caregiverName,
      priority,
      screen: "patient_home",
    },
    "high"
  );
  return task;
}

export async function listCareTasksForPatient(patientId: string): Promise<ICareTask[]> {
  const rows = await CareTask.find({ patientId: new Types.ObjectId(patientId) }).lean();
  const sorted = [...rows].sort((a, b) => {
    const pa = PRI_ORDER[String(a.priority)] ?? 9;
    const pb = PRI_ORDER[String(b.priority)] ?? 9;
    if (pa !== pb) return pa - pb;
    const ta = a.dueAt ? new Date(a.dueAt).getTime() : Number.MAX_SAFE_INTEGER;
    const tb = b.dueAt ? new Date(b.dueAt).getTime() : Number.MAX_SAFE_INTEGER;
    if (ta !== tb) return ta - tb;
    return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime();
  });
  return sorted as unknown as ICareTask[];
}

export async function listCareTasksForCaregiver(
  caregiverId: string,
  patientId: string
): Promise<ICareTask[]> {
  const ok = await careAccess.findApprovedRelationship(caregiverId, patientId);
  if (!ok) {
    const err = new Error("FORBIDDEN") as Error & { status?: number };
    err.status = 403;
    throw err;
  }
  const rows = await CareTask.find({
    patientId: new Types.ObjectId(patientId),
    caregiverId: new Types.ObjectId(caregiverId),
  }).lean();
  return rows as unknown as ICareTask[];
}

export async function markCareTaskDone(
  patientId: string,
  taskId: string
): Promise<ICareTask> {
  if (!Types.ObjectId.isValid(taskId)) {
    const err = new Error("NOT_FOUND") as Error & { status?: number };
    err.status = 404;
    throw err;
  }
  const task = await CareTask.findOne({
    _id: new Types.ObjectId(taskId),
    patientId: new Types.ObjectId(patientId),
    status: "pending",
  });
  if (!task) {
    const err = new Error("NOT_FOUND") as Error & { status?: number };
    err.status = 404;
    throw err;
  }
  task.status = "done";
  task.doneAt = new Date();
  await task.save();
  const patient = await User.findById(patientId).select("name").lean();
  const patientName = String((patient as { name?: string })?.name || "").trim() || "Patient";
  await dispatchPatientAlert(
    patientId,
    "care_task_done",
    "Task completed",
    `${patientName} completed: ${task.title}`,
    { taskId: String(task._id), title: task.title }
  );
  return task;
}
