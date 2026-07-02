package com.example.recallai.data

import com.example.recallai.data.remote.ActivityLogRequest
import com.example.recallai.data.remote.ApiClient
import com.example.recallai.data.remote.MoodLogRequest
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeofenceSyncRepository @Inject constructor() {
    private val api get() = ApiClient.api

    suspend fun reportLocationState(outsideAllZones: Boolean, outsideCount: Int, totalZones: Int) =
        withContext(Dispatchers.IO) {
            val state = if (outsideAllZones) "outside_all_zones" else "inside_safe_zone"
            api.logActivity(
                ActivityLogRequest(
                    // Backend enum only accepts activity-like values.
                    // Use "walking" while preserving geofence signal in description/feedback.
                    type = "walking",
                    name = "Patient geofence check",
                    description = "State=$state ($outsideCount/$totalZones outside zones)",
                    duration = 0,
                    difficulty = if (outsideAllZones) "HIGH" else "LOW",
                    feedback = if (outsideAllZones) "alert_triggered" else "normal"
                )
            )
        }

    suspend fun reportRiskMood(outsideAllZones: Boolean) = withContext(Dispatchers.IO) {
        val score = if (outsideAllZones) 2 else 7
        val note = if (outsideAllZones) {
            "Geofence alert: patient outside all safe zones."
        } else {
            "Geofence status stable."
        }
        api.logMood(
            MoodLogRequest(
                score = score,
                note = note,
                context = "geofence_monitoring",
                activities = listOf("geofence")
            )
        )
    }
}
