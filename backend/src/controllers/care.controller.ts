import { Request, Response } from "express";
import { Types } from "mongoose";
import { CareAlert } from "../models/CareAlert";
import { User } from "../models/User";
import * as careService from "../services/careService";
import * as careTaskService from "../services/careTaskService";

function handleCareError(res: Response, err: unknown) {
  const e = err as Error & { status?: number };
  const status = e.status || 500;
  const map: Record<string, string> = {
    FORBIDDEN: "Forbidden",
    INVALID_INVITE: "Invalid or already used invite",
    EXPIRED_INVITE: "Invite expired",
    INVALID_ROLE: "Caregiver role required",
    SELF: "Cannot link to yourself",
    NOT_FOUND: "Not found",
    INVALID_STATE: "Invalid relationship state",
    ALREADY_LINKED: "Already linked",
    BLOCKED: "Relationship blocked",
    INVALID_COORDS: "Invalid coordinates",
    TITLE_REQUIRED: "Task title is required",
  };
  const code = e.message;
  const message = map[code] || e.message || "Server error";
  return res.status(status).json({ message, code });
}

function serializeCareTask(task: unknown) {
  const source = task as { toObject?: () => Record<string, unknown> };
  const plain: Record<string, unknown> =
    typeof source.toObject === "function"
      ? source.toObject()
      : { ...(task as Record<string, unknown>) };
  return {
    ...plain,
    _id: String(plain._id ?? ""),
    patientId: plain.patientId != null ? String(plain.patientId) : undefined,
    caregiverId: plain.caregiverId != null ? String(plain.caregiverId) : undefined,
    dueAt:
      plain.dueAt instanceof Date
        ? plain.dueAt.toISOString()
        : plain.dueAt != null
          ? String(plain.dueAt)
          : undefined,
    doneAt:
      plain.doneAt instanceof Date
        ? plain.doneAt.toISOString()
        : plain.doneAt != null
          ? String(plain.doneAt)
          : undefined,
    createdAt:
      plain.createdAt instanceof Date
        ? plain.createdAt.toISOString()
        : plain.createdAt != null
          ? String(plain.createdAt)
          : undefined,
    updatedAt:
      plain.updatedAt instanceof Date
        ? plain.updatedAt.toISOString()
        : plain.updatedAt != null
          ? String(plain.updatedAt)
          : undefined,
  };
}

export const postQrCreate = async (req: Request, res: Response) => {
  try {
    const out = await careService.createInviteQr(req.user.id);
    return res.status(201).json({
      token: out.token,
      expiresAt: out.expiresAt.toISOString(),
    });
  } catch (err) {
    return handleCareError(res, err);
  }
};

export const postQrScan = async (req: Request, res: Response) => {
  try {
    const { token } = req.body as { token?: string };
    if (!token || typeof token !== "string") {
      return res.status(400).json({ message: "token required" });
    }
    const rel = await careService.caregiverRequestFromQrToken(req.user.id, token);
    const populated = await careService.relationshipForApi((rel._id as Types.ObjectId).toString());
    return res.status(201).json({ relationship: populated });
  } catch (err) {
    return handleCareError(res, err);
  }
};

export const postInviteGenerate = async (req: Request, res: Response) => {
  try {
    const method = (req.body as { method?: string }).method;
    if (method === "code") {
      const out = await careService.createInviteCode(req.user.id);
      return res.status(201).json({
        shortCode: out.shortCode,
        expiresAt: out.expiresAt.toISOString(),
      });
    }
    if (method === "qr") {
      const out = await careService.createInviteQr(req.user.id);
      return res.status(201).json({
        token: out.token,
        expiresAt: out.expiresAt.toISOString(),
      });
    }
    return res.status(400).json({ message: "method must be qr or code" });
  } catch (err) {
    return handleCareError(res, err);
  }
};

export const postInviteRequest = async (req: Request, res: Response) => {
  try {
    const { token, code } = req.body as { token?: string; code?: string };
    if (token && typeof token === "string") {
      const rel = await careService.caregiverRequestFromQrToken(
        req.user.id,
        token
      );
      const populated = await careService.relationshipForApi((rel._id as Types.ObjectId).toString());
      return res.status(201).json({ relationship: populated });
    }
    if (code && typeof code === "string") {
      const rel = await careService.caregiverRequestFromCode(req.user.id, code);
      const populated = await careService.relationshipForApi((rel._id as Types.ObjectId).toString());
      return res.status(201).json({ relationship: populated });
    }
    return res.status(400).json({ message: "token or code required" });
  } catch (err) {
    return handleCareError(res, err);
  }
};

export const postApprove = async (req: Request, res: Response) => {
  try {
    const { id } = req.params;
    const rel = await careService.approveRequest(req.user.id, id);
    const populated = await careService.relationshipForApi((rel._id as Types.ObjectId).toString());
    return res.json({ relationship: populated });
  } catch (err) {
    return handleCareError(res, err);
  }
};

export const postReject = async (req: Request, res: Response) => {
  try {
    const { id } = req.params;
    const rel = await careService.rejectRequest(req.user.id, id);
    const populated = await careService.relationshipForApi((rel._id as Types.ObjectId).toString());
    return res.json({ relationship: populated });
  } catch (err) {
    return handleCareError(res, err);
  }
};

export const postPatientLiveLocation = async (req: Request, res: Response) => {
  try {
    const { lat, lng } = req.body as { lat?: unknown; lng?: unknown };
    const la = typeof lat === "number" ? lat : Number(lat);
    const ln = typeof lng === "number" ? lng : Number(lng);
    await careService.updatePatientLiveLocation(req.user.id, la, ln);
    return res.json({ ok: true });
  } catch (err) {
    return handleCareError(res, err);
  }
};

export const getMyCaregivers = async (req: Request, res: Response) => {
  try {
    const rows = await careService.listCaregiversForPatient(req.user.id);
    return res.json(rows);
  } catch (err) {
    return handleCareError(res, err);
  }
};

export const getMyPatients = async (req: Request, res: Response) => {
  try {
    const rows = await careService.listPatientsForCaregiver(req.user.id);
    return res.json(rows);
  } catch (err) {
    return handleCareError(res, err);
  }
};

export const getPending = async (req: Request, res: Response) => {
  try {
    const rows = await careService.listPendingForPatient(req.user.id);
    return res.json(rows);
  } catch (err) {
    return handleCareError(res, err);
  }
};

export const patchPermissions = async (req: Request, res: Response) => {
  try {
    const { id } = req.params;
    const rel = await careService.patchRelationshipPermissions(
      req.user.id,
      id,
      req.body || {}
    );
    const populated = await careService.relationshipForApi((rel._id as Types.ObjectId).toString());
    return res.json({ relationship: populated });
  } catch (err) {
    return handleCareError(res, err);
  }
};

export const postEmergency = async (req: Request, res: Response) => {
  try {
    const body = req.body as {
      metadata?: Record<string, unknown>;
      message?: string;
      type?: string;
      lat?: unknown;
      lng?: unknown;
    };
    const meta: Record<string, unknown> = {
      ...(body?.metadata && typeof body.metadata === "object" ? body.metadata : {}),
    };
    if (typeof body?.message === "string" && body.message.trim()) {
      meta.message = body.message.trim();
    }
    if (typeof body?.type === "string" && body.type.trim()) {
      meta.type = body.type.trim();
    }
    if (body?.lat != null && body?.lng != null) {
      const la = Number(body.lat);
      const ln = Number(body.lng);
      if (Number.isFinite(la) && Number.isFinite(ln)) {
        meta.lat = la;
        meta.lng = ln;
      }
    }
    const out = await careService.notifyEmergency(req.user.id, meta);
    return res.status(201).json({
      alertId: out.alert._id,
      caregiverIds: out.caregiverIds.map((x) => x.toString()),
    });
  } catch (err) {
    return handleCareError(res, err);
  }
};

export const postAlertDispatch = async (req: Request, res: Response) => {
  try {
    const { type, title, body, metadata } = req.body as {
      type?: string;
      title?: string;
      body?: string;
      metadata?: Record<string, unknown>;
    };
    if (!type || !title || !body) {
      return res.status(400).json({ message: "type, title, body required" });
    }
    const out = await careService.dispatchPatientAlert(
      req.user.id,
      type,
      title,
      body,
      metadata
    );
    return res.status(201).json({
      alertId: out.alert._id,
      caregiverIds: out.caregiverIds.map((x) => x.toString()),
    });
  } catch (err) {
    return handleCareError(res, err);
  }
};

export const getAlertsForCaregiver = async (req: Request, res: Response) => {
  try {
    const cg = new Types.ObjectId(req.user.id);
    const mine = await CareAlert.find({
      caregiverIds: cg,
    })
      .sort({ createdAt: -1 })
      .limit(100)
      .populate("patientId", "name profileImageUrl")
      .lean();
    const out = mine.map((a: any) => ({
      ...a,
      unread: !(a.readBy || []).some(
        (r: any) => String(r.caregiverId) === req.user.id
      ),
    }));
    return res.json(out);
  } catch (err) {
    return handleCareError(res, err);
  }
};

export const patchAlertRead = async (req: Request, res: Response) => {
  try {
    const { id } = req.params;
    await careService.markAlertReadForCaregiver(req.user.id, id);
    return res.json({ ok: true });
  } catch (err) {
    return handleCareError(res, err);
  }
};

export const postAlertsMarkAllRead = async (req: Request, res: Response) => {
  try {
    await careService.markAllAlertsReadForCaregiver(req.user.id);
    return res.json({ ok: true });
  } catch (err) {
    return handleCareError(res, err);
  }
};

export const getAlertsUnreadCount = async (req: Request, res: Response) => {
  try {
    const n = await careService.countUnreadAlertsForCaregiver(req.user.id);
    return res.json({ count: n });
  } catch (err) {
    return handleCareError(res, err);
  }
};

export const getWatchlist = async (req: Request, res: Response) => {
  try {
    if (req.user.role !== "caregiver") {
      return res.status(403).json({ message: "Caregiver only" });
    }
    const rows = await careService.getWatchlistSnapshot(req.user.id);
    return res.json(rows);
  } catch (err) {
    return handleCareError(res, err);
  }
};

export const postPatientShareLocation = async (req: Request, res: Response) => {
  try {
    if (req.user.role !== "patient") {
      return res.status(403).json({ message: "Patient only" });
    }
    const { address, lat, lng } = req.body as {
      address?: string;
      lat?: unknown;
      lng?: unknown;
    };
    const addr = String(address || "").trim();
    if (!addr) {
      return res.status(400).json({ message: "address required" });
    }
    const la = lat != null ? Number(lat) : undefined;
    const ln = lng != null ? Number(lng) : undefined;
    const out = await careService.patientShareLocation(
      req.user.id,
      addr,
      Number.isFinite(la as number) ? (la as number) : undefined,
      Number.isFinite(ln as number) ? (ln as number) : undefined
    );
    return res.status(201).json({
      alertId: out.alert._id,
      caregiverIds: out.caregiverIds.map((x) => x.toString()),
    });
  } catch (err) {
    return handleCareError(res, err);
  }
};

export const postCareTask = async (req: Request, res: Response) => {
  try {
    if (req.user.role !== "caregiver") {
      return res.status(403).json({ message: "Caregiver only" });
    }
    const { patientId, title, description, priority, dueAt } = req.body as {
      patientId?: string;
      title?: string;
      description?: string;
      priority?: string;
      dueAt?: string | null;
    };
    if (!patientId || typeof patientId !== "string") {
      return res.status(400).json({ message: "patientId required" });
    }
    const task = await careTaskService.createCareTask(req.user.id, patientId, {
      title: title || "",
      description,
      priority: priority as any,
      dueAt: dueAt ?? undefined,
    });
    return res.status(201).json(serializeCareTask(task));
  } catch (err) {
    return handleCareError(res, err);
  }
};

export const getCareTasks = async (req: Request, res: Response) => {
  try {
    const patientId = req.query.patientId as string | undefined;
    if (req.user.role === "patient") {
      const tasks = await careTaskService.listCareTasksForPatient(req.user.id);
      const out = await Promise.all(
        (tasks as any[]).map(async (t) => {
          const c = await User.findById(t.caregiverId).select("name").lean();
          return {
            ...serializeCareTask(t),
            caregiverName: String((c as { name?: string })?.name || "").trim() || "Caregiver",
          };
        })
      );
      return res.json(out);
    }
    if (req.user.role === "caregiver") {
      if (!patientId || typeof patientId !== "string") {
        return res.status(400).json({ message: "patientId query required" });
      }
      const tasks = await careTaskService.listCareTasksForCaregiver(
        req.user.id,
        patientId
      );
      return res.json((tasks as any[]).map(serializeCareTask));
    }
    return res.status(403).json({ message: "Forbidden" });
  } catch (err) {
    return handleCareError(res, err);
  }
};

export const patchCareTaskDone = async (req: Request, res: Response) => {
  try {
    if (req.user.role !== "patient") {
      return res.status(403).json({ message: "Patient only" });
    }
    const { id } = req.params;
    const task = await careTaskService.markCareTaskDone(req.user.id, id);
    return res.json(serializeCareTask(task));
  } catch (err) {
    return handleCareError(res, err);
  }
};

export const deleteRelationship = async (req: Request, res: Response) => {
  try {
    const { id } = req.params;
    await careService.removeRelationship(req.user.id, id);
    return res.status(204).send();
  } catch (err) {
    return handleCareError(res, err);
  }
};
