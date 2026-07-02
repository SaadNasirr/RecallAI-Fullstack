package com.example.recallai.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.recallai.R
import com.example.recallai.data.RecallAiPreferences

object ReminderNotificationController {

    const val CHANNEL_ID = "recallai_reminders"
    const val SOFT_CHANNEL_ID = "recallai_reminders_soft"
    const val ALARM_CHANNEL_ID = "recallai_patient_alarms"
    const val CARE_TASK_CHANNEL_ID = "recallai_care_tasks"
    const val EMERGENCY_PUSH_CHANNEL_ID = "recallai_emergency_push"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ch = NotificationChannel(
            CHANNEL_ID,
            "Reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Smart reminders from RecallAI"
            enableVibration(true)
        }
        nm.createNotificationChannel(ch)
    }

    fun ensureSoftChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ch = NotificationChannel(
            SOFT_CHANNEL_ID,
            "Reminder notes",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Softer pings for medications and appointments"
            enableVibration(false)
        }
        nm.createNotificationChannel(ch)
    }

    fun ensureAlarmChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ch = NotificationChannel(
            ALARM_CHANNEL_ID,
            "Alarms",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Patient-set alarms — sound continues until Done or Dismiss"
            enableVibration(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(ch)
    }

    fun ensureCareTaskChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ch = NotificationChannel(
            CARE_TASK_CHANNEL_ID,
            "Care tasks",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Tasks your caregiver assigns for you"
            enableVibration(true)
        }
        nm.createNotificationChannel(ch)
    }

    fun ensureEmergencyPushChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ch = NotificationChannel(
            EMERGENCY_PUSH_CHANNEL_ID,
            "Emergency & safety",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Patient emergencies and urgent care signals"
            enableVibration(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setBypassDnd(true)
            }
        }
        nm.createNotificationChannel(ch)
    }

    /** Runtime permission (API 33+) + app notification toggle in system settings. */
    fun canShowNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** OS permission only — reminders are not gated by the in-app activity toggle. */
    fun shouldDeliverReminderAlarm(context: Context): Boolean = canShowNotifications(context)

    /**
     * Patient alarms must not be suppressed by the in-app reminder toggle — only OS notification rules apply.
     */
    fun shouldDeliverPatientAlarm(context: Context): Boolean = canShowNotifications(context)

    /** Immediate ping so the user can verify reminders will appear. */
    fun postTestNotification(context: Context) {
        if (!shouldDeliverReminderAlarm(context)) return
        ensureChannel(context)
        val body = "If you see this, reminder alerts can appear. Keep notifications allowed for RecallAI."
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("RecallAI — test reminder")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(TEST_NOTIFICATION_ID, notif)
    }

    private const val TEST_NOTIFICATION_ID = 91001
}
