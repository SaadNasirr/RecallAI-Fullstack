package com.example.recallai.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.recallai.data.local.PatientAlarmEntity

object PatientAlarmScheduler {

    const val EXTRA_ALARM_ID = "patient_alarm_id"
    const val EXTRA_KIND = "patient_alarm_kind"
    const val KIND_FIRE = "fire"
    const val KIND_GRACE = "grace"

    private const val TAG = "PatientAlarmScheduler"

    fun schedule(context: Context, alarm: PatientAlarmEntity) {
        cancelFireAndGrace(context, alarm.id)
        if (!alarm.enabled) return
        val trigger = alarm.nextTriggerAt
        if (trigger <= System.currentTimeMillis()) {
            Log.w(TAG, "Skip schedule patient alarm ${alarm.id}: trigger in past")
            return
        }
        val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context, alarm.id, KIND_FIRE)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmMgr.canScheduleExactAlarms()) {
                    alarmMgr.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
                } else {
                    alarmMgr.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
                }
            } else {
                alarmMgr.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Exact alarm blocked", e)
            alarmMgr.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        } catch (e: Exception) {
            Log.e(TAG, "Patient alarm schedule failed id=${alarm.id}", e)
            runCatching {
                alarmMgr.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            }
        }
    }

    fun scheduleGraceCheck(context: Context, alarmId: Long, atMillis: Long) {
        val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context, alarmId, KIND_GRACE)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmMgr.canScheduleExactAlarms()) {
                alarmMgr.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            } else {
                alarmMgr.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            }
        } catch (e: SecurityException) {
            alarmMgr.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        } catch (e: Exception) {
            Log.e(TAG, "Grace schedule failed id=$alarmId", e)
            runCatching {
                alarmMgr.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            }
        }
    }

    fun cancelFireAndGrace(context: Context, alarmId: Long) {
        val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmMgr.cancel(pendingIntent(context, alarmId, KIND_FIRE))
        alarmMgr.cancel(pendingIntent(context, alarmId, KIND_GRACE))
    }

    private fun pendingIntent(context: Context, alarmId: Long, kind: String): PendingIntent {
        val intent = Intent(context, PatientAlarmReceiver::class.java).apply {
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_KIND, kind)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(alarmId, kind),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun requestCode(alarmId: Long, kind: String): Int {
        val base = (alarmId xor (alarmId ushr 32)).toInt() and 0xfffff
        val k = when (kind) {
            KIND_FIRE -> 0
            KIND_GRACE -> 1
            else -> 2
        }
        return 5_800_000 + base * 4 + k
    }
}
