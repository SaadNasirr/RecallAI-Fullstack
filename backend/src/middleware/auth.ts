import { Request, Response, NextFunction } from "express";
import jwt from "jsonwebtoken";
import { User, IUser } from "../models/User";
import { Types } from "mongoose";

// Extend Express Request type to include user
declare global {
  namespace Express {
    interface Request {
      user?: any;
    }
  }
}

export const auth = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const token = req.header("Authorization")?.replace("Bearer ", "");

    if (!token) {
      return res.status(401).json({ message: "Authentication required" });
    }

    const decoded = jwt.verify(
      token,
      process.env.JWT_SECRET || "your-secret-key"
    ) as { userId: string };

    const user = await User.findById(decoded.userId);

    if (!user) {
      return res.status(401).json({ message: "User not found" });
    }

    // Attach user with a consistent `id` string field so controllers can use req.user.id
    const userObj = user.toObject() as any;
    req.user = {
      ...userObj,
      id: (user._id as Types.ObjectId).toString(),
    };
    next();
  } catch (error) {
    res.status(401).json({ message: "Invalid authentication token" });
  }
};
