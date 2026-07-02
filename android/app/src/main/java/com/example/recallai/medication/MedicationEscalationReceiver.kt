package com.example.recallai.medication

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.recallai.notifications.RecallNotifications
import org.json.JSONArray
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.absoluteValue

class MedicationEscalationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == "com.example.recallai.medication.DOSE_REMINDER") {
            val medicationId = intent.getStringExtra("medication_id").orEmpty()
            val name = intent.getStringExtra("medication_name").orEmpty().ifBlank { "Medication" }
            val timeLabel = intent.getStringExtra("medication_time_label").orEmpty().ifBlank { "scheduled time" }
            val message = "Time for $name ($timeLabel). Please mark as taken."
            RecallNotifications.show(
                context = context,
                kind = RecallNotifications.Kind.Medication,
                title = "Medication due now",
                body = message,
                notificationId = 9500 + medicationId.hashCode().absoluteValue % 2000,
                ignoreActivityToggle = true
            )
            MedicationEscalationScheduler.scheduleDailyDose(context, medicationId, name, timeLabel)
            return
        }
        val medsPrefs = context.getSharedPreferences("care_toolkit_prefs", Context.MODE_PRIVATE)
        val alertsPrefs = context.getSharedPreferences("med_alert_state", Context.MODE_PRIVATE)
        val raw = medsPrefs.getString("medications", "[]") ?: "[]"
        val arr = JSONArray(raw)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        var alertsSent = 0

        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.optString("name", "Medication")
            val timeLabel = o.optString("timeLabel", "scheduled")
            val taken = o.optBoolean("takenToday", false)
            val skipped = o.optBoolean("skippedToday", false)
            if (taken || skipped) continue

            val id = o.optString("id", "med_$i")
            val dedupeKey = "alert_${id}_$today"
            if (alertsPrefs.getBoolean(dedupeKey, false)) continue

            val message = "Dose pending: $name at $timeLabel. Please check in now."
            RecallNotifications.show(
                context = context,
                kind = RecallNotifications.Kind.Medication,
                title = "Medication reminder",
                body = message,
                notificationId = 9200 + i,
                ignoreActivityToggle = true
            )
            alertsPrefs.edit().putBoolean(dedupeKey, true).apply()
            alertsSent++
        }

        if (alertsSent > 0) {
            RecallNotifications.show(
                context = context,
                kind = RecallNotifications.Kind.Medication,
                title = "Medication follow-up",
                body = "$alertsSent medication alert(s) need follow-up.",
                notificationId = 9299,
                ignoreActivityToggle = true
            )
        }
    }
}

object MedicationEscalationScheduler {
    private const val REQUEST_CODE = 5107

    fun schedule(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, MedicationEscalationReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val intervalMs = 30L * 60L * 1000L
        val firstAt = System.currentTimeMillis() + (2L * 60L * 1000L)
        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            firstAt,
            intervalMs,
            pendingIntent
        )
    }

    fun scheduleDailyDose(context: Context, medicationId: String, name: String, timeLabel: String) {
        val hourMinute = parseHourMinute(timeLabel) ?: return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val requestCode = 6000 + medicationId.hashCode().absoluteValue % 20_000
        val intent = Intent(context, MedicationEscalationReceiver::class.java).apply {
            action = "com.example.recallai.medication.DOSE_REMINDER"
            putExtra("medication_id", medicationId)
            putExtra("medication_name", name)
            putExtra("medication_time_label", timeLabel)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hourMinute.first)
            set(Calendar.MINUTE, hourMinute.second)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(Calendar.getInstance())) add(Calendar.DAY_OF_YEAR, 1)
        }
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            cal.timeInMillis,
            pendingIntent
        )
    }

    private fun parseHourMinute(label: String): Pair<Int, Int>? {
        val normalized = label.trim().uppercase(Locale.US)
        val regex = Regex("""^(\d{1,2}):(\d{2})\s*(AM|PM)$""")
        val match = regex.find(normalized) ?: return null
        val rawHour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null
        if (rawHour !in 1..12 || minute !in 0..59) return null
        val meridian = match.groupValues[3]
        val hour24 = when {
            meridian == "AM" && rawHour == 12 -> 0
            meridian == "PM" && rawHour != 12 -> rawHour + 12
            else -> rawHour
        }
        return hour24 to minute
    }
}
