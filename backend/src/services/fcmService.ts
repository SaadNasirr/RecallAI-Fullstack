import path from "path";
import { Types } from "mongoose";
import { User } from "../models/User";
import { logger } from "../utils/logger";

type AndroidPriority = "high" | "normal";

let adminInited = false;
let adminApp: import("firebase-admin").app.App | null = null;

function tryInitFirebaseAdmin(): import("firebase-admin").app.App | null {
  if (adminInited) return adminApp;
  adminInited = true;
  const credPath = process.env.FIREBASE_SERVICE_ACCOUNT_PATH?.trim();
  if (!credPath) {
    logger.info("FCM disabled: set FIREBASE_SERVICE_ACCOUNT_PATH to a service account JSON file.");
    return null;
  }
  try {
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const admin = require("firebase-admin") as typeof import("firebase-admin");
    if (!admin.apps.length) {
      const resolved = path.isAbsolute(credPath)
        ? credPath
        : path.join(process.cwd(), credPath);
      adminApp = admin.initializeApp({
        credential: admin.credential.cert(resolved),
      });
    } else {
      adminApp = admin.app();
    }
    return adminApp;
  } catch (err) {
    logger.warn("FCM firebase-admin init failed", err);
    adminApp = null;
    return null;
  }
}

export async function sendPushToUsers(
  userIds: string[],
  notification: { title: string; body: string },
  data: Record<string, string> = {},
  androidPriority: AndroidPriority = "normal"
): Promise<void> {
  const app = tryInitFirebaseAdmin();
  if (!app || userIds.length === 0) return;
  const admin = require("firebase-admin") as typeof import("firebase-admin");
  const oids = userIds.filter((id) => Types.ObjectId.isValid(id)).map((id) => new Types.ObjectId(id));
  if (!oids.length) return;
  const users = await User.find({ _id: { $in: oids } })
    .select("fcmTokens name")
    .lean();
  const tokens = new Set<string>();
  for (const u of users as any[]) {
    for (const row of u.fcmTokens || []) {
      if (row?.token && typeof row.token === "string") tokens.add(row.token);
    }
  }
  const list = [...tokens];
  if (!list.length) {
    logger.warn(
      `FCM: no device tokens for ${userIds.length} user(s) — open the app while logged in on each phone.`
    );
    return;
  }
  const chunks: string[][] = [];
  for (let i = 0; i < list.length; i += 400) chunks.push(list.slice(i, i + 400));
  for (const chunk of chunks) {
    try {
      await admin.messaging().sendEachForMulticast({
        tokens: chunk,
        notification: {
          title: notification.title,
          body: notification.body,
        },
        data: {
          ...data,
          title: notification.title,
          body: notification.body,
        },
        android: {
          priority: androidPriority,
        },
        apns: {
          headers:
            androidPriority === "high"
              ? { "apns-priority": "10" }
              : { "apns-priority": "5" },
        },
      });
    } catch (e) {
      logger.warn("FCM sendEachForMulticast failed", e);
    }
  }
}

export async function sendPushToCaregivers(
  caregiverIds: Types.ObjectId[],
  notification: { title: string; body: string },
  data: Record<string, string> = {},
  androidPriority: AndroidPriority = "normal"
): Promise<void> {
  const ids = caregiverIds.map((x) => x.toString());
  await sendPushToUsers(ids, notification, data, androidPriority);
}
