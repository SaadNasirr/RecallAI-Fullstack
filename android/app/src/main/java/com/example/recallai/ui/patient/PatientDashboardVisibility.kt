package com.example.recallai.ui.patient

import com.example.recallai.data.local.PatientAlarmEntity
import com.example.recallai.data.local.PatientAlarmRepeatMode
import com.example.recallai.data.local.ReminderEntity
import com.example.recallai.data.local.ReminderRepeatMode
import com.example.recallai.data.local.ReminderStatus
import com.example.recallai.ui.screens.PatientAssignedCareTask
/**
 * Rules for what appears on the patient dashboard schedule and task lists.
 *
 * Completed items are hidden immediately (no arbitrary timer).
 * One-time reminders/alarms also drop off today's timeline shortly after their slot passes.
 */
object PatientDashboardVisibility {

    /** How long after a slot time it may still show on today's timeline. */
    const val SCHEDULE_PAST_GRACE_MS = 5L * 60L * 1000L

    fun visibleCareTasks(tasks: List<PatientAssignedCareTask>): List<PatientAssignedCareTask> =
        tasks.filter { !it.isDone }

    fun doneCareTaskTitles(tasks: List<PatientAssignedCareTask>): Set<String> =
        tasks.filter { it.isDone }.map { it.title.trim().lowercase() }.toSet()

    fun visibleAlarms(
        alarms: List<PatientAlarmEntity>,
        dayStartMs: Long,
        nowMs: Long = System.currentTimeMillis()
    ): List<PatientAlarmEntity> =
        alarms.filter { alarm ->
            if (!alarm.enabled) return@filter false
            if (alarm.repeatMode == PatientAlarmRepeatMode.ONCE &&
                alarm.pendingAckSinceMs == null &&
                alarm.nextTriggerAt < dayStartMs
            ) {
                return@filter false
            }
            if (alarm.repeatMode == PatientAlarmRepeatMode.ONCE &&
                alarm.pendingAckSinceMs == null &&
                alarm.nextTriggerAt < nowMs - SCHEDULE_PAST_GRACE_MS
            ) {
                return@filter false
            }
            true
        }

    fun shouldShowOnTodayTimeline(epochMs: Long, nowMs: Long = System.currentTimeMillis()): Boolean =
        epochMs >= nowMs - SCHEDULE_PAST_GRACE_MS

    fun isReminderVisibleInSchedule(
        reminder: ReminderEntity,
        dayStartMs: Long,
        dayEndMs: Long,
        slotEpochMs: Long,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        if (reminder.status != ReminderStatus.PENDING) return false
        if (slotEpochMs !in dayStartMs until dayEndMs) return false
        return shouldShowOnTodayTimeline(slotEpochMs, nowMs)
    }

    fun isCareRemoteSlotVisible(title: String, excludeTitles: Set<String>): Boolean {
        val key = title.trim().lowercase()
        if (key.isBlank()) return true
        return key !in excludeTitles
    }
}
