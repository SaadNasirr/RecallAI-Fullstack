package com.example.recallai.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.recallai.data.local.RecallDatabase
import com.example.recallai.data.local.ReminderEntity
import com.example.recallai.data.local.ReminderRepeatMode
import kotlinx.coroutines.runBlocking

object ReminderScheduler {
    const val EXTRA_REMINDER_ID = "reminder_id"
    const val EXTRA_IS_WARNING = "is_warning"
    const val EXTRA_SOFT_DELIVERY = "soft_delivery"
    private const val TAG = "ReminderScheduler"

    fun schedule(context: Context, reminder: ReminderEntity) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = System.currentTimeMillis()
        var r = reminder
        if (r.repeatMode != ReminderRepeatMode.NONE && r.datetime <= now) {
            val n = ReminderScheduleHelper.computeNextTrigger(r, now - 1) ?: run {
                Log.w(TAG, "No next slot for recurring reminder id=${r.id}")
                return
            }
            r = r.copy(datetime = n, updatedAt = now)
            if (r.id != 0L) {
                runBlocking {
                    RecallDatabase.getInstance(context.applicationContext).reminderDao().update(r)
                }
            }
        }
        if (r.datetime <= now) {
            Log.w(TAG, "Skip schedule: reminder time already passed id=${r.id}")
            return
        }

        val soft = false
        scheduleTrigger(alarm, context, r.datetime, r.id, isWarning = false, soft = soft)

        if (r.warn10Min) {
            val warnAt = r.datetime - (10L * 60L * 1000L)
            if (warnAt > now) {
                scheduleTrigger(alarm, context, warnAt, r.id, isWarning = true, soft = soft)
            }
        }
    }

    private fun scheduleTrigger(
        alarm: AlarmManager,
        context: Context,
        triggerAtMillis: Long,
        reminderId: Long,
        isWarning: Boolean,
        soft: Boolean
    ) {
        val pi = pendingIntent(context, reminderId, isWarning, soft)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarm.canScheduleExactAlarms()) {
                    alarm.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pi
                    )
                } else {
                    Log.w(TAG, "Exact alarms not allowed; using inexact wake trigger at $triggerAtMillis")
                    alarm.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pi
                    )
                }
            } else {
                alarm.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pi
                )
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Exact alarm blocked; falling back to inexact", e)
            alarm.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pi
            )
        } catch (e: Exception) {
            Log.e(TAG, "Alarm schedule failed id=$reminderId", e)
            runCatching {
                alarm.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pi
                )
            }
        }
    }

    fun cancel(context: Context, reminderId: Long) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.cancel(pendingIntent(context, reminderId, isWarning = false, soft = false))
        alarm.cancel(pendingIntent(context, reminderId, isWarning = false, soft = true))
        alarm.cancel(pendingIntent(context, reminderId, isWarning = true, soft = false))
        alarm.cancel(pendingIntent(context, reminderId, isWarning = true, soft = true))
    }

    private fun pendingIntent(context: Context, reminderId: Long, isWarning: Boolean, soft: Boolean): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            putExtra(EXTRA_REMINDER_ID, reminderId)
            putExtra(EXTRA_IS_WARNING, isWarning)
            putExtra(EXTRA_SOFT_DELIVERY, soft)
        }
        val requestCode = (reminderId.toInt() * 4) + (if (isWarning) 2 else 0) + if (soft) 1 else 0
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

