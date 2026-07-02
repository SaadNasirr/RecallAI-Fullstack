package com.example.recallai.reminders

import com.example.recallai.data.local.ReminderEntity
import com.example.recallai.data.local.ReminderRepeatMode
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

object ReminderUiFormatter {

    fun formatTime12(t: LocalTime): String =
        DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()).format(t)

    fun maskToLabel(mask: Int): String {
        if (mask == 0) return ""
        val names = mutableListOf<String>()
        for (i in 0 until 7) {
            if ((mask shr i) and 1 == 1) {
                names.add(DayOfWeek.of(i + 1).getDisplayName(TextStyle.FULL, Locale.getDefault()))
            }
        }
        return names.joinToString(", ")
    }

    fun formatScheduleClause(
        repeatMode: ReminderRepeatMode,
        mask: Int,
        scheduleNote: String
    ): String = when (repeatMode) {
        ReminderRepeatMode.NONE -> "on $scheduleNote".trim()
        ReminderRepeatMode.DAILY -> "every day"
        ReminderRepeatMode.WEEKLY -> {
            val m = maskToLabel(mask)
            if (m.isBlank()) "every week" else "every $m"
        }
    }

    fun formatReminderListSubtitle(entity: ReminderEntity): String {
        val zone = ZoneId.systemDefault()
        val zdt = Instant.ofEpochMilli(entity.datetime).atZone(zone)
        val dateFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
        val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
        val timePart = timeFmt.format(zdt)
        return when (entity.repeatMode) {
            ReminderRepeatMode.NONE -> "${dateFmt.format(zdt)} · $timePart"
            ReminderRepeatMode.DAILY -> "Daily · $timePart"
            ReminderRepeatMode.WEEKLY -> {
                val m = maskToLabel(entity.daysOfWeekMask)
                if (m.isBlank()) "Weekly · $timePart" else "$m · $timePart"
            }
        }
    }

    fun formatEpochDate(ms: Long): String {
        val fmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
        val d = LocalDate.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault())
        return fmt.format(d)
    }

    fun formatEpochDay(epochDay: Long): String {
        val d = LocalDate.ofEpochDay(epochDay)
        val fmt = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault())
        return fmt.format(d)
    }
}
