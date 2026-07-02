package com.example.recallai.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.recallai.R
import com.example.recallai.data.local.RecallDatabase
import com.example.recallai.data.local.ReminderRepeatMode
import com.example.recallai.data.local.ReminderStatus
import kotlinx.coroutines.runBlocking

class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(ReminderScheduler.EXTRA_REMINDER_ID, -1L)
        if (id <= 0L) return
        val isWarning = intent.getBooleanExtra(ReminderScheduler.EXTRA_IS_WARNING, false)
        val soft = intent.getBooleanExtra(ReminderScheduler.EXTRA_SOFT_DELIVERY, false)

        if (!ReminderNotificationController.shouldDeliverReminderAlarm(context.applicationContext)) {
            return
        }

        val db = RecallDatabase.getInstance(context)
        val reminder = runBlocking { db.reminderDao().getById(id) } ?: return

        val title = if (isWarning) {
            "Upcoming reminder"
        } else {
            "Reminder due"
        }
        val body = if (isWarning) {
            "In 10 minutes: ${reminder.title}"
        } else {
            "You have ${reminder.title} right now"
        }

        if (soft) {
            ReminderNotificationController.ensureSoftChannel(context.applicationContext)
        } else {
            ReminderNotificationController.ensureChannel(context.applicationContext)
        }

        val channelId = if (soft) {
            ReminderNotificationController.SOFT_CHANNEL_ID
        } else {
            ReminderNotificationController.CHANNEL_ID
        }

        val notif = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(
                if (soft) NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_HIGH
            )
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId(id, isWarning, soft), notif)

        if (!isWarning) {
            when (reminder.repeatMode) {
                ReminderRepeatMode.NONE ->
                    runBlocking {
                        db.reminderDao().setStatus(reminder.id, ReminderStatus.COMPLETED)
                    }
                ReminderRepeatMode.DAILY,
                ReminderRepeatMode.WEEKLY -> {
                    val next = ReminderScheduleHelper.computeNextTrigger(reminder, System.currentTimeMillis())
                    if (next != null) {
                        val updated = reminder.copy(datetime = next, updatedAt = System.currentTimeMillis())
                        runBlocking {
                            db.reminderDao().update(updated)
                            ReminderScheduler.schedule(context.applicationContext, updated)
                        }
                    }
                }
            }
        }
    }

    private fun notificationId(reminderId: Long, isWarning: Boolean, soft: Boolean): Int =
        (reminderId.toInt() * 8) + (if (isWarning) 2 else 0) + if (soft) 1 else 0
}

