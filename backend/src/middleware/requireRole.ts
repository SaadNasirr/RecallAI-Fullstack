import { Request, Response, NextFunction } from "express";

export function requireRole(...roles: ("patient" | "caregiver")[]) {
  return (req: Request, res: Response, next: NextFunction) => {
    const role = req.user?.role as string | undefined;
    if (!role || !roles.includes(role as "patient" | "caregiver")) {
      return res.status(403).json({ message: "Forbidden" });
    }
    next();
  };
}
