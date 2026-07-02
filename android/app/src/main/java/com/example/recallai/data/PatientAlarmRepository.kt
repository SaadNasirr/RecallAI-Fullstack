package com.example.recallai.data

import android.content.Context
import com.example.recallai.data.CareToolkitRepository
import com.example.recallai.data.local.PatientAlarmDao
import com.example.recallai.data.local.PatientAlarmEntity
import com.example.recallai.data.local.PatientAlarmRepeatMode
import com.example.recallai.reminders.PatientAlarmScheduleHelper
import com.example.recallai.reminders.PatientAlarmScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

@Singleton
class PatientAlarmRepository @Inject constructor(
    private val dao: PatientAlarmDao,
    @ApplicationContext private val context: Context,
    private val careToolkitRepository: CareToolkitRepository
) {

    val alarms: Flow<List<PatientAlarmEntity>> = dao.observeAll()

    suspend fun save(alarm: PatientAlarmEntity): Long = withContext(Dispatchers.IO) {
        val prepared = prepare(alarm)
        val id = dao.insert(prepared)
        val saved = dao.getById(id) ?: return@withContext id
        PatientAlarmScheduler.schedule(context.applicationContext, saved)
        careToolkitRepository.pushPatientToolkitToServer()
        id
    }

    suspend fun delete(alarm: PatientAlarmEntity) = withContext(Dispatchers.IO) {
        PatientAlarmScheduler.cancelFireAndGrace(context.applicationContext, alarm.id)
        dao.delete(alarm)
        careToolkitRepository.pushPatientToolkitToServer()
    }

    /** After reboot: reschedule enabled alarms whose fire time is stale. */
    suspend fun rescheduleAllEnabledFromStorage() = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        dao.getEnabled().forEach { row ->
            var next = row.nextTriggerAt
            if (next <= now) {
                val computed = PatientAlarmScheduleHelper.computeNextTrigger(row, now)
                next = computed ?: return@forEach
            }
            val updated = row.copy(nextTriggerAt = next, updatedAt = now)
            dao.update(updated)
            val saved = dao.getById(updated.id) ?: return@forEach
            PatientAlarmScheduler.schedule(context.applicationContext, saved)
        }
    }

    private fun prepare(alarm: PatientAlarmEntity): PatientAlarmEntity {
        val now = System.currentTimeMillis()
        val next = when (alarm.repeatMode) {
            PatientAlarmRepeatMode.ONCE -> alarm.nextTriggerAt
            PatientAlarmRepeatMode.DAILY,
            PatientAlarmRepeatMode.WEEKLY -> {
                PatientAlarmScheduleHelper.computeNextTrigger(alarm.copy(pendingAckSinceMs = null), now - 1)
                    ?: alarm.nextTriggerAt
            }
        }
        return alarm.copy(
            nextTriggerAt = next,
            updatedAt = now,
            pendingAckSinceMs = null
        )
    }
}
