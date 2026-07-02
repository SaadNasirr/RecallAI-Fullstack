package com.example.recallai.reminders

import com.example.recallai.data.local.PatientAlarmEntity
import com.example.recallai.data.local.PatientAlarmRepeatMode
import java.util.Calendar
import java.util.TimeZone

internal object PatientAlarmScheduleHelper {

    private val zone: TimeZone get() = TimeZone.getDefault()

    fun computeNextTrigger(entity: PatientAlarmEntity, strictlyAfterMs: Long): Long? {
        return when (entity.repeatMode) {
            PatientAlarmRepeatMode.ONCE -> {
                if (entity.nextTriggerAt > strictlyAfterMs) entity.nextTriggerAt else null
            }
            PatientAlarmRepeatMode.DAILY -> nextDaily(entity.hour, entity.minute, strictlyAfterMs)
            PatientAlarmRepeatMode.WEEKLY -> nextWeekly(entity.hour, entity.minute, entity.daysOfWeekMask, strictlyAfterMs)
        }
    }

    fun occurrencesToday(entity: PatientAlarmEntity, dayStartMs: Long, dayEndMs: Long): Long? {
        val cal = Calendar.getInstance(zone)
        when (entity.repeatMode) {
            PatientAlarmRepeatMode.ONCE -> {
                val t = entity.nextTriggerAt
                return if (t in dayStartMs until dayEndMs) t else null
            }
            PatientAlarmRepeatMode.DAILY -> {
                cal.timeInMillis = dayStartMs
                cal.set(Calendar.HOUR_OF_DAY, entity.hour)
                cal.set(Calendar.MINUTE, entity.minute)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val at = cal.timeInMillis
                return if (at in dayStartMs until dayEndMs) at else null
            }
            PatientAlarmRepeatMode.WEEKLY -> {
                cal.timeInMillis = dayStartMs
                if (!maskIncludesDay(entity.daysOfWeekMask, cal)) return null
                cal.set(Calendar.HOUR_OF_DAY, entity.hour)
                cal.set(Calendar.MINUTE, entity.minute)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val at = cal.timeInMillis
                return if (at in dayStartMs until dayEndMs) at else null
            }
        }
    }

    private fun nextDaily(hour: Int, minute: Int, strictlyAfterMs: Long): Long {
        val cal = Calendar.getInstance(zone)
        cal.timeInMillis = strictlyAfterMs
        for (step in 0..366) {
            if (step > 0) cal.add(Calendar.DAY_OF_MONTH, 1)
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, minute)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            if (cal.timeInMillis > strictlyAfterMs) return cal.timeInMillis
        }
        return strictlyAfterMs + 24L * 60L * 60L * 1000L
    }

    private fun nextWeekly(hour: Int, minute: Int, mask: Int, strictlyAfterMs: Long): Long? {
        if (mask == 0) return null
        val cal = Calendar.getInstance(zone)
        cal.timeInMillis = strictlyAfterMs
        for (step in 0..366) {
            if (step > 0) cal.add(Calendar.DAY_OF_MONTH, 1)
            if (!maskIncludesDay(mask, cal)) continue
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, minute)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            if (cal.timeInMillis > strictlyAfterMs) return cal.timeInMillis
        }
        return null
    }

    private fun maskIncludesDay(mask: Int, cal: Calendar): Boolean {
        val bit = calendarDayToBit(cal.get(Calendar.DAY_OF_WEEK))
        return (mask shr bit) and 1 == 1
    }

    private fun calendarDayToBit(dow: Int): Int = when (dow) {
        Calendar.MONDAY -> 0
        Calendar.TUESDAY -> 1
        Calendar.WEDNESDAY -> 2
        Calendar.THURSDAY -> 3
        Calendar.FRIDAY -> 4
        Calendar.SATURDAY -> 5
        Calendar.SUNDAY -> 6
        else -> 0
    }
}
