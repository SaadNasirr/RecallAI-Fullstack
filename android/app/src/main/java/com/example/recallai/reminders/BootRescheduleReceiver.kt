package com.example.recallai.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.recallai.data.local.RecallDatabase
import kotlinx.coroutines.runBlocking

class BootRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val db = RecallDatabase.getInstance(context)
        runBlocking {
            val upcoming = db.reminderDao().getUpcomingPending(System.currentTimeMillis())
            upcoming.forEach { ReminderScheduler.schedule(context, it) }

            val now = System.currentTimeMillis()
            val alarmDao = db.patientAlarmDao()
            alarmDao.getEnabled().forEach { row ->
                var next = row.nextTriggerAt
                if (next <= now) {
                    next = PatientAlarmScheduleHelper.computeNextTrigger(row, now) ?: return@forEach
                }
                val updated = row.copy(nextTriggerAt = next, updatedAt = now)
                alarmDao.update(updated)
                val saved = alarmDao.getById(updated.id) ?: return@forEach
                PatientAlarmScheduler.schedule(context, saved)
            }
        }
    }
}

