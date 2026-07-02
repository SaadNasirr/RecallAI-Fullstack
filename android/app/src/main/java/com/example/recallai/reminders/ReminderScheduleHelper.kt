package com.example.recallai.reminders

import com.example.recallai.data.local.ReminderEntity
import com.example.recallai.data.local.ReminderRepeatMode
import java.util.Calendar
import java.util.TimeZone

/**
 * Computes the next wall-clock trigger for a reminder row (same semantics as patient alarms).
 */
object ReminderScheduleHelper {

    private val zone: TimeZone get() = TimeZone.getDefault()

    fun hourMinuteFromEpoch(ms: Long): Pair<Int, Int> {
        val cal = Calendar.getInstance(zone)
        cal.timeInMillis = ms
        return cal.get(Calendar.HOUR_OF_DAY) to cal.get(Calendar.MINUTE)
    }

    fun computeNextTrigger(entity: ReminderEntity, strictlyAfterMs: Long): Long? {
        val (h, min) = hourMinuteFromEpoch(entity.datetime)
        return when (entity.repeatMode) {
            ReminderRepeatMode.NONE -> {
                if (entity.datetime > strictlyAfterMs) entity.datetime else null
            }
            ReminderRepeatMode.DAILY -> nextDaily(h, min, strictlyAfterMs)
            ReminderRepeatMode.WEEKLY -> {
                if (entity.daysOfWeekMask == 0) null
                else nextWeekly(h, min, entity.daysOfWeekMask, strictlyAfterMs)
            }
        }
    }

    fun firstDailyTrigger(hour: Int, minute: Int, fromMs: Long): Long {
        return nextDaily(hour, minute, fromMs - 1)
    }

    fun firstWeeklyTrigger(hour: Int, minute: Int, mask: Int, fromMs: Long): Long? {
        if (mask == 0) return null
        return nextWeekly(hour, minute, mask, fromMs - 1)
    }

    fun combineLocalDateAndTime(dateStartOfDayMs: Long, hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance(zone)
        cal.timeInMillis = dateStartOfDayMs
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
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
