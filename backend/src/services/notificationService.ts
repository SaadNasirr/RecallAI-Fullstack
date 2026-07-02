import { dispatchPatientAlert } from "./careService";

/**
 * Notifies linked caregivers when a patient leaves a safe zone.
 */
export async function sendAlertToLinkedCaregivers(
  patientId: string,
  title: string,
  body: string,
  metadata?: Record<string, unknown>
) {
  return dispatchPatientAlert(
    patientId,
    "geofence_exit",
    title,
    body,
    { ...(metadata || {}), source: "geofence" }
  );
}
