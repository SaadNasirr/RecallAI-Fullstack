package com.example.recallai.care

import android.content.Context
import com.example.recallai.data.remote.CareAlertDto
import com.example.recallai.notifications.RecallNotifications

/**
 * Shows system-tray notifications for new patient→caregiver care alerts.
 * Complements FCM (works when push is delayed or the app is in the foreground).
 */
object CareAlertNotifier {

    private const val PREFS = "care_alert_notifier"
    private const val KEY_SEEN_IDS = "seen_alert_ids"

    fun notifyNewAlerts(context: Context, alerts: List<CareAlertDto>) {
        if (!RecallNotifications.canPost(context)) return
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val seen = prefs.getStringSet(KEY_SEEN_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()

        val unread = alerts.filter { it.unread == true && !it._id.isNullOrBlank() }
        val fresh = unread.filter { it._id !in seen }
        if (fresh.isEmpty()) return

        // Notify the newest unread alerts we have not surfaced yet (cap per poll).
        fresh.sortedByDescending { it.createdAt.orEmpty() }.take(3).forEach { alert ->
            val id = alert._id ?: return@forEach
            val title = alert.title?.trim().takeIf { !it.isNullOrBlank() } ?: "Care alert"
            val body = alert.body?.trim().takeIf { !it.isNullOrBlank() } ?: "New update from your patient."
            val type = alert.type?.lowercase().orEmpty()
            RecallNotifications.showFromFcmData(
                context = app,
                dataType = type.ifBlank { "care_alert" },
                title = title,
                body = body,
                screen = "alert_center"
            )
            seen.add(id)
        }

        // Keep set bounded.
        if (seen.size > 200) {
            val keep = alerts.mapNotNull { it._id }.take(150).toSet()
            seen.retainAll(keep)
        }
        prefs.edit().putStringSet(KEY_SEEN_IDS, seen).apply()
    }

    fun resetSeen(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_SEEN_IDS)
            .apply()
    }
}
