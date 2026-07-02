package com.example.recallai.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.recallai.data.local.PatientAlarmEntity
import com.example.recallai.data.local.PatientAlarmDao
import com.example.recallai.data.local.RecallDatabase
import kotlinx.coroutines.runBlocking

/**
 * Fires patient alarms and grace checks (missed acknowledgement).
 */
class PatientAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(PatientAlarmScheduler.EXTRA_ALARM_ID, -1L)
        val kind = intent.getStringExtra(PatientAlarmScheduler.EXTRA_KIND)
            ?: PatientAlarmScheduler.KIND_FIRE
        if (id <= 0L) return

        val db = RecallDatabase.getInstance(context.applicationContext)
        runBlocking {
            val dao = db.patientAlarmDao()
            val alarm = dao.getById(id) ?: return@runBlocking
            when (kind) {
                PatientAlarmScheduler.KIND_FIRE -> handleFire(context.applicationContext, dao, alarm)
                PatientAlarmScheduler.KIND_GRACE -> handleGrace(context.applicationContext, dao, alarm)
            }
        }
    }

    private suspend fun handleFire(context: Context, dao: PatientAlarmDao, alarm: PatientAlarmEntity) {
        val now = System.currentTimeMillis()
        dao.setPendingAck(alarm.id, now)
        ReminderNotificationController.ensureAlarmChannel(context)
        if (!ReminderNotificationController.shouldDeliverPatientAlarm(context)) {
            android.util.Log.w(
                "PatientAlarmReceiver",
                "Notification permission or system toggle blocked — grant RecallAI notifications for alarms."
            )
        }
        ContextCompat.startForegroundService(
            context,
            Intent(context, PatientAlarmSoundService::class.java).apply {
                putExtra(PatientAlarmSoundService.EXTRA_ALARM_ID, alarm.id)
                putExtra(PatientAlarmSoundService.EXTRA_LABEL, alarm.label)
            }
        )
        PatientAlarmScheduler.scheduleGraceCheck(context, alarm.id, now + GRACE_MS)
    }

    private suspend fun handleGrace(context: Context, dao: PatientAlarmDao, alarm: PatientAlarmEntity) {
        if (alarm.pendingAckSinceMs == null) return

        PatientAlarmCareNotifier.notifyMissedOrDismissed(
            title = "Alarm missed",
            body = "${alarm.label} — no confirmation in time."
        )
        PatientAlarmSoundService.stop(context.applicationContext)
        val updated = PatientAlarmPostFire.patchAfterNegative(alarm)
        dao.update(updated)
        NotificationManagerCompat.from(context).cancel(PatientAlarmNotifications.notificationId(alarm.id))
        PatientAlarmScheduler.cancelFireAndGrace(context, alarm.id)
        if (updated.enabled) {
            PatientAlarmScheduler.schedule(context, updated)
        }
    }

    companion object {
        private const val GRACE_MS = 15L * 60L * 1000L
    }
}
