package com.example.recallai.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.recallai.data.AuthManager
import com.example.recallai.notifications.RecallNotifications
import com.example.recallai.data.remote.ApiClient
import com.example.recallai.data.remote.GeofenceEventRequest
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return
        if (AuthManager.userRole != "patient" || AuthManager.token.isNullOrBlank()) {
            return
        }
        val transition = event.geofenceTransition
        val eventType = when (transition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> "entered"
            Geofence.GEOFENCE_TRANSITION_EXIT -> "exited"
            else -> return
        }
        val loc = event.triggeringLocation
        val lat = loc?.latitude ?: 0.0
        val lng = loc?.longitude ?: 0.0
        val ids = event.triggeringGeofences?.map { it.requestId }?.filter { it.isNotBlank() } ?: return
        if (ids.isEmpty()) return

        val pending = goAsync()
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.IO)
        scope.launch {
            try {
                val api = ApiClient.api
                for (id in ids) {
                    runCatching {
                        api.reportGeofenceEvent(
                            GeofenceEventRequest(
                                geofenceId = id,
                                eventType = eventType,
                                lat = lat,
                                lng = lng
                            )
                        )
                    }
                }
            } finally {
                pending.finish()
            }
        }
        val title = if (eventType == "entered") "Entered safe zone" else "Left safe zone"
        val body = if (ids.size == 1) {
            "Zone update recorded."
        } else {
            "Zone update recorded (${ids.size} zones)."
        }
        RecallNotifications.show(
            context = context,
            kind = RecallNotifications.Kind.Geofence,
            title = title,
            body = body,
            ignoreActivityToggle = true
        )
    }
}
