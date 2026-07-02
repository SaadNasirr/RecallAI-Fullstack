import { Request, Response } from "express";
import { User } from "../models/User";
import { Session } from "../models/Session";
import bcrypt from "bcryptjs";
import jwt from "jsonwebtoken";

export const register = async (req: Request, res: Response) => {
  try {
    const { name, email, password, role, gender, phone, liveLat, liveLng } = req.body;
    const normalizedRole = String(role || "").trim().toLowerCase();
    const normalizedGender = String(gender || "").trim().toLowerCase();
    const phoneTrimmed = String(phone ?? "").trim();
    if (!name || !email || !password || !normalizedRole || !phoneTrimmed) {
      return res.status(400).json({
        message: "Name, email, password, phone, and role are required.",
      });
    }
    if (!["patient", "caregiver"].includes(normalizedRole)) {
      return res.status(400).json({ message: "Role must be patient or caregiver." });
    }
    if (normalizedGender && !["male", "female"].includes(normalizedGender)) {
      return res.status(400).json({ message: "Gender must be male or female." });
    }
    // Check if user exists
    const existingUser = await User.findOne({ email });
    if (existingUser) {
      return res.status(409).json({ message: "Email already in use." });
    }
    // Hash password
    const hashedPassword = await bcrypt.hash(password, 10);
    let lat: number | undefined;
    let lng: number | undefined;
    if (normalizedRole === "patient") {
      const la = liveLat != null ? Number(liveLat) : NaN;
      const ln = liveLng != null ? Number(liveLng) : NaN;
      if (!Number.isFinite(la) || !Number.isFinite(ln)) {
        return res.status(400).json({
          message: "Patients must provide a valid live location (latitude and longitude).",
        });
      }
      lat = la;
      lng = ln;
    }

    // Create user
    const user = new User({
      name,
      email,
      password: hashedPassword,
      role: normalizedRole,
      gender: normalizedGender || undefined,
      phone: phoneTrimmed,
      ...(lat != null && lng != null ? { liveLat: lat, liveLng: lng } : {}),
    });
    await user.save();
    // Respond
    res.status(201).json({
      user: {
        _id: user._id,
        name: user.name,
        email: user.email,
        role: user.role,
        gender: user.gender,
        phone: user.phone,
        liveLat: user.liveLat,
        liveLng: user.liveLng,
      },
      message: "User registered successfully.",
    });
  } catch (error) {
    res.status(500).json({ message: "Server error", error });
  }
};

export const login = async (req: Request, res: Response) => {
  try {
    const { email, password, role } = req.body;
    const normalizedRole = String(role || "").trim().toLowerCase();

    // Validate input
    if (!email || !password || !normalizedRole) {
      return res
        .status(400)
        .json({ message: "Email, password, and role are required." });
    }
    if (!["patient", "caregiver"].includes(normalizedRole)) {
      return res.status(400).json({ message: "Role must be patient or caregiver." });
    }

    // Find user
    const user = await User.findOne({ email });
    if (!user) {
      return res.status(401).json({ message: "Invalid email or password." });
    }

    // Verify password
    const isPasswordValid = await bcrypt.compare(password, user.password);
    if (!isPasswordValid) {
      return res.status(401).json({ message: "Invalid email or password." });
    }
    if (!user.role) {
      (user as any).role = normalizedRole;
      await user.save();
    }
    if (String(user.role || "").toLowerCase() !== normalizedRole) {
      return res.status(401).json({ message: "This account belongs to a different role." });
    }

    // Generate JWT token
    const token = jwt.sign(
      { userId: user._id },
      process.env.JWT_SECRET || "your-secret-key",
      { expiresIn: "24h" }
    );

    // Create session
    const expiresAt = new Date();
    expiresAt.setHours(expiresAt.getHours() + 24); // 24 hours from now

    const session = new Session({
      userId: user._id,
      token,
      expiresAt,
      deviceInfo: req.headers["user-agent"],
    });
    await session.save();

    user.lastActiveAt = new Date();
    await user.save();

    // Respond with user data and token
    res.json({
      user: {
        _id: user._id,
        name: user.name,
        email: user.email,
        role: user.role,
        gender: user.gender,
        phone: user.phone,
        liveLat: user.liveLat,
        liveLng: user.liveLng,
      },
      token,
      message: "Login successful",
    });
  } catch (error) {
    res.status(500).json({ message: "Server error", error });
  }
};

export const postDeviceToken = async (req: Request, res: Response) => {
  try {
    const { token } = req.body as { token?: string };
    if (!token || typeof token !== "string" || token.length < 20) {
      return res.status(400).json({ message: "FCM token required" });
    }
    const user = await User.findById(req.user.id);
    if (!user) {
      return res.status(404).json({ message: "User not found" });
    }
    const trimmed = token.trim().slice(0, 512);
    const existing = (user as any).fcmTokens as { token: string; updatedAt: Date }[] | undefined;
    const kept = (existing || []).filter((t) => t.token !== trimmed).slice(-12);
    kept.push({ token: trimmed, updatedAt: new Date() });
    (user as any).fcmTokens = kept.slice(-15);
    (user as any).lastActiveAt = new Date();
    await user.save();
    res.json({ ok: true });
  } catch (error) {
    res.status(500).json({ message: "Server error", error });
  }
};

export const logout = async (req: Request, res: Response) => {
  try {
    const token = req.header("Authorization")?.replace("Bearer ", "");
    if (token) {
      await Session.deleteOne({ token });
    }
    res.json({ message: "Logged out successfully" });
  } catch (error) {
    res.status(500).json({ message: "Server error", error });
  }
};
