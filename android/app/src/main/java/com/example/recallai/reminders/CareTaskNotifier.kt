package com.example.recallai.reminders

import android.content.Context
import com.example.recallai.notifications.RecallNotifications

object CareTaskNotifier {
    private const val PREFS = "recallai_care_task_ids"
    private const val KEY_SEEN = "seen_care_task_ids"

    /**
     * Shows a notification for each task id the patient has not been notified of yet.
     */
    fun notifyNewTasks(context: Context, tasks: List<Pair<String, String>>) {
        if (tasks.isEmpty()) return
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val seen = prefs.getStringSet(KEY_SEEN, emptySet())!!.toMutableSet()

        for ((id, title) in tasks) {
            if (id.isBlank() || id in seen) continue
            seen.add(id)
            val trimmed = title.trim().ifBlank { "Task" }
            RecallNotifications.show(
                context = app,
                kind = RecallNotifications.Kind.CareTask,
                title = "New care task",
                body = "Your caregiver assigned: $trimmed",
                notificationId = notificationIdFor(id),
                ignoreActivityToggle = true,
                screenRoute = "patient_home"
            )
        }
        prefs.edit().putStringSet(KEY_SEEN, seen).apply()
    }

    private fun notificationIdFor(taskId: String): Int {
        var h = taskId.hashCode()
        if (h == Int.MIN_VALUE) h = 0
        return 93_000 + (kotlin.math.abs(h) % 5000)
    }
}
