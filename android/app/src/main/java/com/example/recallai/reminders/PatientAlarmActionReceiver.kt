package com.example.recallai.reminders

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.recallai.R
import com.example.recallai.data.local.PatientAlarmDao
import com.example.recallai.data.local.PatientAlarmEntity
import com.example.recallai.data.local.PatientAlarmRepeatMode
import com.example.recallai.data.local.RecallDatabase
import kotlinx.coroutines.runBlocking

/**
 * Handles alarm acknowledgement actions from notifications (patient tapped Done / Dismiss).
 */
class PatientAlarmActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
        val action = intent.getStringExtra(EXTRA_ACTION) ?: return
        if (alarmId <= 0L) return
        val app = context.applicationContext
        // Stop audio + remove notification FIRST. DB row can be missing; sound must still stop.
        NotificationManagerCompat.from(app).cancel(PatientAlarmNotifications.notificationId(alarmId))
        PatientAlarmSoundService.stop(app)
        PatientAlarmScheduler.cancelFireAndGrace(app, alarmId)

        val db = RecallDatabase.getInstance(app)
        runBlocking {
            val dao = db.patientAlarmDao()
            val alarm = dao.getById(alarmId) ?: return@runBlocking

            when (action) {
                ACTION_ACK -> handleAck(app, dao, alarm)
                ACTION_DISMISS -> handleDismiss(app, dao, alarm)
                else -> Unit
            }
        }
    }

    private suspend fun handleAck(
        context: Context,
        dao: PatientAlarmDao,
        alarm: PatientAlarmEntity
    ) {
        val updated = PatientAlarmPostFire.patchAfterPositive(alarm)
        dao.update(updated)
        if (updated.enabled) {
            PatientAlarmScheduler.schedule(context, updated)
        }
    }

    private suspend fun handleDismiss(
        context: Context,
        dao: PatientAlarmDao,
        alarm: PatientAlarmEntity
    ) {
        PatientAlarmCareNotifier.notifyMissedOrDismissed(
            title = "Alarm dismissed",
            body = "${alarm.label} — dismissed without confirming."
        )
        val updated = PatientAlarmPostFire.patchAfterNegative(alarm)
        dao.update(updated)
        if (updated.enabled) {
            PatientAlarmScheduler.schedule(context, updated)
        }
    }

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_ACTION = "alarm_action"
        const val ACTION_ACK = "ack"
        const val ACTION_DISMISS = "dismiss"

        fun ackIntent(context: Context, alarmId: Long): PendingIntent {
            val i = Intent(context, PatientAlarmActionReceiver::class.java).apply {
                putExtra(EXTRA_ALARM_ID, alarmId)
                putExtra(EXTRA_ACTION, ACTION_ACK)
            }
            return PendingIntent.getBroadcast(
                context,
                requestCode(alarmId, ACTION_ACK),
                i,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        fun dismissIntent(context: Context, alarmId: Long): PendingIntent {
            val i = Intent(context, PatientAlarmActionReceiver::class.java).apply {
                putExtra(EXTRA_ALARM_ID, alarmId)
                putExtra(EXTRA_ACTION, ACTION_DISMISS)
            }
            return PendingIntent.getBroadcast(
                context,
                requestCode(alarmId, ACTION_DISMISS),
                i,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun requestCode(alarmId: Long, action: String): Int {
            val base = (alarmId xor (alarmId ushr 32)).toInt() and 0xfffff
            val k = if (action == ACTION_ACK) 0 else 1
            return 5_900_000 + base * 4 + k
        }
    }
}

/** Pure transitions after an alarm cycle (no Android APIs). */
internal object PatientAlarmPostFire {

    fun patchAfterPositive(alarm: PatientAlarmEntity): PatientAlarmEntity {
        val now = System.currentTimeMillis()
        return when (alarm.repeatMode) {
            PatientAlarmRepeatMode.ONCE ->
                alarm.copy(
                    enabled = false,
                    pendingAckSinceMs = null,
                    updatedAt = now
                )
            PatientAlarmRepeatMode.DAILY,
            PatientAlarmRepeatMode.WEEKLY -> {
                val next = PatientAlarmScheduleHelper.computeNextTrigger(
                    alarm.copy(pendingAckSinceMs = null),
                    now
                )
                if (next == null) {
                    alarm.copy(enabled = false, pendingAckSinceMs = null, updatedAt = now)
                } else {
                    alarm.copy(nextTriggerAt = next, pendingAckSinceMs = null, updatedAt = now)
                }
            }
        }
    }

    fun patchAfterNegative(alarm: PatientAlarmEntity): PatientAlarmEntity {
        val now = System.currentTimeMillis()
        return when (alarm.repeatMode) {
            PatientAlarmRepeatMode.ONCE ->
                alarm.copy(enabled = false, pendingAckSinceMs = null, updatedAt = now)
            PatientAlarmRepeatMode.DAILY,
            PatientAlarmRepeatMode.WEEKLY -> {
                val next = PatientAlarmScheduleHelper.computeNextTrigger(
                    alarm.copy(pendingAckSinceMs = null),
                    now
                )
                if (next == null) {
                    alarm.copy(enabled = false, pendingAckSinceMs = null, updatedAt = now)
                } else {
                    alarm.copy(nextTriggerAt = next, pendingAckSinceMs = null, updatedAt = now)
                }
            }
        }
    }
}
