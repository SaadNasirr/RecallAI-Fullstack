package com.example.recallai.geofence

import android.content.Context
import com.example.recallai.data.AuthManager
import com.example.recallai.data.CareRepository
import com.example.recallai.data.GeofenceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Pulls server-side zones and registers [GeofencingClient] on the patient device.
 * Without this, zones created by a caregiver only reach the phone after the patient opens Safe zones.
 */
object GeofenceForegroundSync {
    private const val PREFS = "geofence_foreground_sync"
    private const val KEY_LAST_MS = "last_sync_ms"
    private const val MIN_INTERVAL_MS = 15_000L

    fun runIfPatient(
        context: Context,
        repository: GeofenceRepository,
        scope: CoroutineScope,
        careRepository: CareRepository? = null
    ) {
        if (AuthManager.userRole != "patient") return
        AuthManager.userId ?: return
        if (AuthManager.token.isNullOrBlank()) return
        val appCtx = context.applicationContext
        if (careRepository != null) {
            PatientLocationSync.requestPush(appCtx, careRepository, scope)
        }
        val prefs = appCtx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_LAST_MS, 0L) < MIN_INTERVAL_MS) return
        scope.launch(Dispatchers.IO) {
            repository.listZones(null).onSuccess { zones ->
                GeofenceRegistrationHelper.registerZonesFromResponses(appCtx, zones)
                prefs.edit().putLong(KEY_LAST_MS, now).apply()
            }
        }
    }
}
