package com.example.recallai.geofence

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.recallai.data.AuthManager
import com.example.recallai.data.CareRepository
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Pushes the patient device's current coordinates to the backend so linked caregivers
 * can see approximate location on the Watch screen.
 */
object PatientLocationSync {
    private const val PREFS = "patient_location_sync"
    private const val KEY_LAST_PUSH_MS = "last_push_ms"
    private const val MIN_INTERVAL_MS = 90_000L

    private fun hasAnyLocationPermission(ctx: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    fun requestPush(context: Context, careRepository: CareRepository, scope: CoroutineScope) {
        if (AuthManager.userRole != "patient") return
        if (AuthManager.token.isNullOrBlank()) return
        val app = context.applicationContext
        if (!hasAnyLocationPermission(app)) return
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_LAST_PUSH_MS, 0L) < MIN_INTERVAL_MS) return
        scope.launch(Dispatchers.IO) {
            runCatching {
                val fused = LocationServices.getFusedLocationProviderClient(app)
                val loc = Tasks.await(
                    fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null),
                    15L,
                    TimeUnit.SECONDS
                ) ?: return@launch
                careRepository.pushPatientLiveLocation(loc.latitude, loc.longitude)
                prefs.edit().putLong(KEY_LAST_PUSH_MS, System.currentTimeMillis()).apply()
            }
        }
    }
}
