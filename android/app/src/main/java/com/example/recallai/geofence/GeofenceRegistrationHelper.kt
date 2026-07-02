package com.example.recallai.geofence

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.recallai.data.remote.GeofenceResponse
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

object GeofenceRegistrationHelper {
    private const val PREFS = "geofence_registration"
    private const val KEY_ZONES = "zones_json"
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    fun persistZones(context: Context, zones: List<GeofenceResponse>) {
        val type = Types.newParameterizedType(List::class.java, GeofenceResponse::class.java)
        val adapter = moshi.adapter<List<GeofenceResponse>>(type)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_ZONES, adapter.toJson(zones))
            .apply()
    }

    fun loadPersistedZones(context: Context): List<GeofenceResponse> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ZONES, null)
            ?: return emptyList()
        return runCatching {
            val type = Types.newParameterizedType(List::class.java, GeofenceResponse::class.java)
            val adapter = moshi.adapter<List<GeofenceResponse>>(type)
            adapter.fromJson(raw) ?: emptyList()
        }.getOrElse { emptyList() }
    }

    fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        }
        return PendingIntent.getBroadcast(context, 91021, intent, flags)
    }

    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED ||
            coarse == PackageManager.PERMISSION_GRANTED
    }

    fun registerZonesFromResponses(context: Context, zones: List<GeofenceResponse>) {
        persistZones(context, zones)
        applyRegistration(context, zones)
    }

    fun reregisterFromDisk(context: Context) {
        applyRegistration(context, loadPersistedZones(context))
    }

    private fun applyRegistration(context: Context, zones: List<GeofenceResponse>) {
        if (!hasLocationPermission(context)) return
        val client: GeofencingClient = LocationServices.getGeofencingClient(context)
        val pi = pendingIntent(context)
        client.removeGeofences(pi).addOnCompleteListener {
            val active = zones.filter { it.isActive }
            if (active.isEmpty()) return@addOnCompleteListener
            val list = active.map { z ->
                Geofence.Builder()
                    .setRequestId(z._id)
                    .setCircularRegion(z.centerLat, z.centerLng, z.radiusMeters.toFloat().coerceAtLeast(50f))
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(
                        Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT
                    )
                    .build()
            }
            val request = GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofences(list)
                .build()
            try {
                client.addGeofences(request, pi)
                    .addOnFailureListener { e ->
                        Log.e("GeofenceReg", "addGeofences failed", e)
                    }
            } catch (e: SecurityException) {
                Log.e("GeofenceReg", "permission", e)
            }
        }
    }

    fun clearRegistration(context: Context) {
        persistZones(context, emptyList())
        val client = LocationServices.getGeofencingClient(context)
        client.removeGeofences(pendingIntent(context))
    }
}
