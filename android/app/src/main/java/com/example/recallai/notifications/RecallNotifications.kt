package com.example.recallai.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.recallai.MainActivity
import com.example.recallai.R
import com.example.recallai.data.RecallAiPreferences
import com.example.recallai.reminders.ReminderNotificationController
import kotlin.math.absoluteValue

/**
 * Single entry point for showing notifications. Always checks OS permission;
 * optional in-app "Activity reminders" toggle only applies to routine reminders.
 */
object RecallNotifications {

    enum class Kind {
        Reminder,
        ReminderSoft,
        CareTask,
        Emergency,
        Geofence,
        Medication,
        CareAlert
    }

    fun ensureAllChannels(context: Context) {
        val app = context.applicationContext
        ReminderNotificationController.ensureChannel(app)
        ReminderNotificationController.ensureSoftChannel(app)
        ReminderNotificationController.ensureAlarmChannel(app)
        ReminderNotificationController.ensureCareTaskChannel(app)
        ReminderNotificationController.ensureEmergencyPushChannel(app)
        ensureGeofenceChannel(app)
        ensureMedicationChannel(app)
        ensureCareAlertChannel(app)
    }

    fun canPost(context: Context): Boolean =
        ReminderNotificationController.canShowNotifications(context.applicationContext)

    /**
     * @param ignoreActivityToggle When true (emergency, tasks, geofence, caregiver alerts),
     *   show even if the user turned off "Activity reminders" in settings.
     */
    fun show(
        context: Context,
        kind: Kind,
        title: String,
        body: String,
        notificationId: Int? = null,
        ignoreActivityToggle: Boolean = kind != Kind.Reminder && kind != Kind.ReminderSoft,
        screenRoute: String? = null
    ) {
        val app = context.applicationContext
        if (!canPost(app)) return
        if (!ignoreActivityToggle && !RecallAiPreferences.isNotifyEnabled(app)) return

        ensureAllChannels(app)
        val channelId = channelFor(kind)
        val id = notificationId ?: stableId(kind, title, body)

        val open = Intent(app, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            screenRoute?.let { putExtra("fcm_screen", it) }
        }
        val pi = PendingIntent.getActivity(
            app,
            id,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val priority = when (kind) {
            Kind.ReminderSoft -> NotificationCompat.PRIORITY_DEFAULT
            else -> NotificationCompat.PRIORITY_HIGH
        }

        val notif = NotificationCompat.Builder(app, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(priority)
            .apply {
                when (kind) {
                    Kind.Emergency, Kind.Geofence ->
                        setCategory(NotificationCompat.CATEGORY_ALARM)
                    Kind.CareTask, Kind.CareAlert ->
                        setCategory(NotificationCompat.CATEGORY_REMINDER)
                    else -> {}
                }
            }
            .build()

        NotificationManagerCompat.from(app).notify(id, notif)
    }

    fun showFromFcmData(
        context: Context,
        dataType: String,
        title: String,
        body: String,
        screen: String?
    ) {
        val kind = when {
            dataType.contains("emergency") -> Kind.Emergency
            dataType.contains("geofence") || dataType.contains("geofence_enter") ||
                dataType.contains("geofence_exit") -> Kind.Geofence
            dataType.contains("missed_task") -> Kind.CareAlert
            dataType.contains("care_task_done") -> Kind.CareAlert
            dataType.contains("care_task") || dataType.contains("task") -> Kind.CareTask
            dataType.contains("mood") -> Kind.CareAlert
            dataType.contains("medication") -> Kind.Medication
            dataType.contains("location") -> Kind.CareAlert
            else -> Kind.CareAlert
        }
        show(
            context = context,
            kind = kind,
            title = title,
            body = body,
            ignoreActivityToggle = true,
            screenRoute = screen
        )
    }

    private fun channelFor(kind: Kind): String = when (kind) {
        Kind.Reminder -> ReminderNotificationController.CHANNEL_ID
        Kind.ReminderSoft -> ReminderNotificationController.SOFT_CHANNEL_ID
        Kind.CareTask -> ReminderNotificationController.CARE_TASK_CHANNEL_ID
        Kind.Emergency -> ReminderNotificationController.EMERGENCY_PUSH_CHANNEL_ID
        Kind.Geofence -> CHANNEL_GEOFENCE
        Kind.Medication -> CHANNEL_MEDICATION
        Kind.CareAlert -> CHANNEL_CARE_ALERT
    }

    private fun stableId(kind: Kind, title: String, body: String): Int {
        val seed = "${kind.name}:$title:$body".hashCode()
        return 20_000 + (seed.absoluteValue % 80_000)
    }

    private const val CHANNEL_GEOFENCE = "recallai_geofence"
    private const val CHANNEL_MEDICATION = "recallai_medication"
    private const val CHANNEL_CARE_ALERT = "recallai_care_alerts"

    private fun ensureGeofenceChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_GEOFENCE,
                "Safe zones",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Entered or left a safe zone"
                enableVibration(true)
            }
        )
    }

    private fun ensureMedicationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MEDICATION,
                "Medication",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Medication dose reminders"
                enableVibration(true)
            }
        )
    }

    private fun ensureCareAlertChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CARE_ALERT,
                "Care alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts from your linked patient or caregiver"
                enableVibration(true)
            }
        )
    }
}
