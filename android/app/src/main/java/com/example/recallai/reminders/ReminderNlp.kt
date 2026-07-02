package com.example.recallai.reminders

import com.example.recallai.data.local.ReminderRepeatMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

data class ReminderDraft(
    val title: String,
    val description: String? = null,
    val datetime: Long,
    val type: String? = null,
    val warn10Min: Boolean = true,
    /** True when we guessed date (e.g., only “8pm” provided). */
    val ambiguous: Boolean = false,
    val repeatMode: ReminderRepeatMode = ReminderRepeatMode.NONE,
    val daysOfWeekMask: Int = 0
)

sealed class ScheduleParseResult {
    data class Ok(
        val repeatMode: ReminderRepeatMode,
        val oneOffDate: LocalDate?,
        val weeklyMask: Int
    ) : ScheduleParseResult()

    data class Ask(val question: String) : ScheduleParseResult()
    data object Unrecognized : ScheduleParseResult()
}

/**
 * Lightweight, local extraction for time-based tasks from user chat.
 */
object ReminderNlp {
    private val timeRegex =
        Regex("""\b(at\s*)?(\d{1,2})(?::(\d{2}))?\s*(am|pm)?\b""", RegexOption.IGNORE_CASE)
    private val tomorrowRegex = Regex("""\btomorrow\b""", RegexOption.IGNORE_CASE)
    private val todayRegex = Regex("""\btoday\b""", RegexOption.IGNORE_CASE)
    private val dateIsoRegex = Regex("""\b(\d{4})-(\d{2})-(\d{2})\b""")
    private val weekdayRegex =
        Regex(
            """\b(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b""",
            RegexOption.IGNORE_CASE
        )
    private val everyDayRegex = Regex("""\bevery\s+day\b""", RegexOption.IGNORE_CASE)
    private val everyRegex = Regex("""\bevery\b""", RegexOption.IGNORE_CASE)
    private val thisPrefixRegex = Regex("""\bthis\s+(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b""", RegexOption.IGNORE_CASE)
    private val nextPrefixRegex = Regex("""\bnext\s+(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b""", RegexOption.IGNORE_CASE)

    private val monthDayYear = Regex(
        """\b(\d{1,2})\s+(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*(?:\s+(\d{4}))?\b""",
        RegexOption.IGNORE_CASE
    )
    private val monthNameDay = Regex(
        """\b(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\s+(\d{1,2})(?:st|nd|rd|th)?(?:\s+(\d{4}))?\b""",
        RegexOption.IGNORE_CASE
    )

    private val monthTokens =
        mapOf(
            "jan" to Month.JANUARY,
            "feb" to Month.FEBRUARY,
            "mar" to Month.MARCH,
            "apr" to Month.APRIL,
            "may" to Month.MAY,
            "jun" to Month.JUNE,
            "jul" to Month.JULY,
            "aug" to Month.AUGUST,
            "sep" to Month.SEPTEMBER,
            "oct" to Month.OCTOBER,
            "nov" to Month.NOVEMBER,
            "dec" to Month.DECEMBER
        )

    private fun mentionedWeekday(lower: String): DayOfWeek? {
        val map =
            mapOf(
                "monday" to DayOfWeek.MONDAY,
                "tuesday" to DayOfWeek.TUESDAY,
                "wednesday" to DayOfWeek.WEDNESDAY,
                "thursday" to DayOfWeek.THURSDAY,
                "friday" to DayOfWeek.FRIDAY,
                "saturday" to DayOfWeek.SATURDAY,
                "sunday" to DayOfWeek.SUNDAY
            )
        val m = weekdayRegex.find(lower) ?: return null
        val key = m.groupValues[1].lowercase(Locale.getDefault())
        return map[key]
    }

    private fun dayToBit(dow: DayOfWeek): Int = dow.value - 1

    fun weekdaysMaskInText(lower: String): Int {
        var mask = 0
        weekdayRegex.findAll(lower).forEach { m ->
            val w = mentionedWeekday(m.value) ?: return@forEach
            mask = mask or (1 shl dayToBit(w))
        }
        return mask
    }

    /** Next occurrence of [weekday] at [time], rolling forward if that instant is already past. */
    private fun resolveWeekdayDate(weekday: DayOfWeek, time: LocalTime, now: LocalDateTime): LocalDate {
        val today = now.toLocalDate()
        val currentDow = today.dayOfWeek.value
        val targetDow = weekday.value
        val addDays = (targetDow - currentDow + 7) % 7
        var candidate = today.plusDays(addDays.toLong())
        var dt = LocalDateTime.of(candidate, time)
        if (dt.isBefore(now.plusMinutes(1))) {
            candidate = candidate.plusWeeks(1)
        }
        return candidate
    }

    private fun parseMonthToken(tok: String): Month? =
        monthTokens.entries.firstOrNull { tok.lowercase(Locale.getDefault()).startsWith(it.key) }?.value

    private fun parseNumericDate(lower: String, now: LocalDateTime): LocalDate? {
        dateIsoRegex.find(lower)?.let {
            val y = it.groupValues[1].toInt()
            val mo = it.groupValues[2].toInt()
            val d = it.groupValues[3].toInt()
            return runCatching { LocalDate.of(y, mo, d) }.getOrNull()
        }
        monthDayYear.find(lower)?.let { m ->
            val d = m.groupValues[1].toIntOrNull() ?: return@let
            val month = parseMonthToken(m.groupValues[2]) ?: return@let
            val y = m.groupValues[3].toIntOrNull() ?: now.year
            return runCatching { LocalDate.of(y, month, d) }.getOrNull()
        }
        monthNameDay.find(lower)?.let { m ->
            val month = parseMonthToken(m.groupValues[1]) ?: return@let
            val d = m.groupValues[2].toIntOrNull() ?: return@let
            val y = m.groupValues[3].toIntOrNull() ?: now.year
            var candidate = runCatching { LocalDate.of(y, month, d) }.getOrNull() ?: return@let
            if (candidate.isBefore(now.toLocalDate()) && m.groupValues[3].isBlank()) {
                candidate = candidate.plusYears(1)
            }
            return candidate
        }
        return null
    }

    fun parseTimeFromText(lower: String): LocalTime? {
        val timeMatch = timeRegex.find(lower) ?: return null
        val hourRaw = timeMatch.groupValues[2].toIntOrNull() ?: return null
        val minRaw = timeMatch.groupValues[3].toIntOrNull() ?: 0
        val ampm = timeMatch.groupValues[4].lowercase(Locale.getDefault()).trim()
        var hour = hourRaw.coerceIn(0, 23)
        if (ampm == "pm" && hour in 1..11) hour += 12
        if (ampm == "am" && hour == 12) hour = 0
        return runCatching { LocalTime.of(hour, minRaw.coerceIn(0, 59)) }.getOrNull()
    }

    /**
     * Parse date / repeat part (no time required). Used by therapist reminder wizard step 2.
     */
    fun parseSchedulePhrase(text: String, nowMs: Long = System.currentTimeMillis()): ScheduleParseResult {
        val raw = text.trim()
        if (raw.length < 2) return ScheduleParseResult.Unrecognized
        val lower = raw.lowercase(Locale.getDefault())
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMs), zone)

        if (everyDayRegex.containsMatchIn(lower)) {
            return ScheduleParseResult.Ok(ReminderRepeatMode.DAILY, null, 0)
        }

        val beforeTime = timeRegex.find(lower)?.let { lower.substring(0, it.range.first) } ?: lower

        if (everyRegex.containsMatchIn(beforeTime)) {
            val mask = weekdaysMaskInText(beforeTime)
            if (mask != 0) {
                return ScheduleParseResult.Ok(ReminderRepeatMode.WEEKLY, null, mask)
            }
        }

        thisPrefixRegex.find(lower)?.groupValues?.getOrNull(1)?.let { w ->
            val dow = mentionedWeekday(w) ?: return@let
            val date = now.toLocalDate().with(TemporalAdjusters.nextOrSame(dow))
            return ScheduleParseResult.Ok(ReminderRepeatMode.NONE, date, 0)
        }
        nextPrefixRegex.find(lower)?.groupValues?.getOrNull(1)?.let { w ->
            val dow = mentionedWeekday(w) ?: return@let
            val date = now.toLocalDate().with(TemporalAdjusters.next(dow))
            return ScheduleParseResult.Ok(ReminderRepeatMode.NONE, date, 0)
        }

        if (tomorrowRegex.containsMatchIn(lower)) {
            return ScheduleParseResult.Ok(ReminderRepeatMode.NONE, now.toLocalDate().plusDays(1), 0)
        }
        if (todayRegex.containsMatchIn(lower)) {
            return ScheduleParseResult.Ok(ReminderRepeatMode.NONE, now.toLocalDate(), 0)
        }

        parseNumericDate(lower, now)?.let { d ->
            return ScheduleParseResult.Ok(ReminderRepeatMode.NONE, d, 0)
        }

        val wd = mentionedWeekday(lower)
        if (wd != null) {
            if (!everyRegex.containsMatchIn(beforeTime) &&
                !thisPrefixRegex.containsMatchIn(lower) &&
                !nextPrefixRegex.containsMatchIn(lower)
            ) {
                return ScheduleParseResult.Ask(
                    "Did you mean a specific upcoming ${wd.getDisplayName(TextStyle.FULL, Locale.getDefault())}, " +
                        "or every week on that day? Reply with “this ${wd.getDisplayName(TextStyle.FULL, Locale.getDefault())}” " +
                        "or “every ${wd.getDisplayName(TextStyle.FULL, Locale.getDefault())}”."
                )
            }
        }

        return ScheduleParseResult.Unrecognized
    }

    fun looksLikeReminderIntent(text: String): Boolean {
        val lower = text.lowercase(Locale.getDefault())
        return lower.contains("remind") ||
            lower.contains("reminder") ||
            lower.contains("set a reminder") ||
            lower.contains("set reminder") ||
            lower.contains("meeting at") ||
            lower.contains("appointment at") ||
            (lower.contains("meeting") && timeRegex.containsMatchIn(lower)) ||
            (lower.contains("appointment") && timeRegex.containsMatchIn(lower))
    }

    /** Topic fragment after “remind me to …” etc., may be blank. */
    fun extractReminderTopic(text: String): String {
        val t = text.trim()
        val lower = t.lowercase(Locale.getDefault())
        val patterns =
            listOf(
                Regex("""^remind\s+me\s+to\s+""", RegexOption.IGNORE_CASE),
                Regex("""^remind\s+me\s+about\s+""", RegexOption.IGNORE_CASE),
                Regex("""^reminder\s+to\s+""", RegexOption.IGNORE_CASE),
                Regex("""^set\s+(a\s+)?reminder\s+(to|for)\s+""", RegexOption.IGNORE_CASE)
            )
        var rest = t
        for (p in patterns) {
            val m = p.find(lower) ?: continue
            rest = t.substring(m.range.last + 1).trim()
            break
        }
        return rest
            .replace(timeRegex, "")
            .replace(weekdayRegex, "")
            .replace(everyRegex, "")
            .replace(tomorrowRegex, "")
            .replace(todayRegex, "")
            .replace(dateIsoRegex, "")
            .trim()
            .trim { it == ',' || it == '.' }
            .take(120)
    }

    /** One-line parse when message already contains time + schedule hints. */
    fun tryParseCompleteReminder(userText: String, nowMs: Long = System.currentTimeMillis()): ReminderDraft? {
        val text = userText.trim()
        if (text.length < 4) return null
        val lower = text.lowercase(Locale.getDefault())
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMs), zone)
        val time = parseTimeFromText(lower) ?: return null

        val beforeTime = timeRegex.find(lower)?.let { lower.substring(0, it.range.first) } ?: ""

        val repeatMode: ReminderRepeatMode
        val daysMask: Int
        val baseDate: LocalDate

        when {
            everyDayRegex.containsMatchIn(beforeTime) -> {
                repeatMode = ReminderRepeatMode.DAILY
                daysMask = 0
                baseDate = now.toLocalDate()
            }
            everyRegex.containsMatchIn(beforeTime) -> {
                val mask = weekdaysMaskInText(beforeTime)
                if (mask == 0) return null
                repeatMode = ReminderRepeatMode.WEEKLY
                daysMask = mask
                baseDate = now.toLocalDate()
            }
            else -> {
                repeatMode = ReminderRepeatMode.NONE
                daysMask = 0
                val iso = dateIsoRegex.find(lower)
                val explicitDate: LocalDate? = iso?.let {
                    val y = it.groupValues[1].toInt()
                    val m = it.groupValues[2].toInt()
                    val d = it.groupValues[3].toInt()
                    runCatching { LocalDate.of(y, m, d) }.getOrNull()
                }
                val wantsTomorrow = tomorrowRegex.containsMatchIn(lower)
                val wantsToday = todayRegex.containsMatchIn(lower)
                val weekday = mentionedWeekday(lower)
                val thisM = thisPrefixRegex.find(lower)
                val nextM = nextPrefixRegex.find(lower)
                baseDate =
                    when {
                        thisM != null -> {
                            val dow = mentionedWeekday(thisM.groupValues[1]) ?: now.dayOfWeek
                            resolveWeekdayDate(dow, time, now)
                        }
                        nextM != null -> {
                            val dow = mentionedWeekday(nextM.groupValues[1]) ?: now.dayOfWeek
                            now.toLocalDate().with(java.time.temporal.TemporalAdjusters.next(dow))
                        }
                        explicitDate != null -> explicitDate
                        parseNumericDate(lower, now) != null -> parseNumericDate(lower, now)!!
                        wantsTomorrow -> now.toLocalDate().plusDays(1)
                        wantsToday -> now.toLocalDate()
                        weekday != null -> resolveWeekdayDate(weekday, time, now)
                        else -> now.toLocalDate()
                    }
            }
        }

        var dt = LocalDateTime.of(baseDate, time)
        var ambiguous =
            repeatMode == ReminderRepeatMode.NONE &&
                dateIsoRegex.find(lower) == null &&
                parseNumericDate(lower, now) == null &&
                !tomorrowRegex.containsMatchIn(lower) &&
                !todayRegex.containsMatchIn(lower) &&
                mentionedWeekday(lower) == null &&
                !thisPrefixRegex.containsMatchIn(lower) &&
                !nextPrefixRegex.containsMatchIn(lower)

        if (ambiguous && dt.isBefore(now.plusMinutes(1))) {
            dt = dt.plusDays(1)
        }

        val whenMs =
            when (repeatMode) {
                ReminderRepeatMode.NONE -> dt.atZone(zone).toInstant().toEpochMilli()
                ReminderRepeatMode.DAILY,
                ReminderRepeatMode.WEEKLY -> {
                    val calMs = java.util.Calendar.getInstance().timeInMillis
                    val h = time.hour
                    val mi = time.minute
                    when (repeatMode) {
                        ReminderRepeatMode.DAILY ->
                            ReminderScheduleHelper.firstDailyTrigger(h, mi, calMs)
                        ReminderRepeatMode.WEEKLY ->
                            ReminderScheduleHelper.firstWeeklyTrigger(h, mi, daysMask, calMs)
                                ?: dt.atZone(zone).toInstant().toEpochMilli()
                        else -> dt.atZone(zone).toInstant().toEpochMilli()
                    }
                }
            }

        val title = guessTitle(text)
        val type = guessType(lower)

        return ReminderDraft(
            title = title,
            description = text,
            datetime = whenMs,
            type = type,
            warn10Min = true,
            ambiguous = ambiguous,
            repeatMode = repeatMode,
            daysOfWeekMask = daysMask
        )
    }

    fun extract(userText: String, nowMs: Long = System.currentTimeMillis()): ReminderDraft? =
        tryParseCompleteReminder(userText, nowMs)

    private fun guessTitle(text: String): String {
        val cleaned = text
            .replace(Regex("""\s+at\s+\d{1,2}(:\d{2})?\s*(am|pm)?\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\b(today|tomorrow)\b""", RegexOption.IGNORE_CASE), "")
            .replace(weekdayRegex, "")
            .replace(everyRegex, "")
            .replace(everyDayRegex, "")
            .trim()
        val title = cleaned.takeIf { it.length >= 4 } ?: text.take(60)
        return title.take(60)
    }

    private fun guessType(lower: String): String? = when {
        lower.contains("doctor") || lower.contains("appointment") || lower.contains("clinic") -> "appointment"
        lower.contains("medicine") || lower.contains("pill") || lower.contains("tablet") -> "medication"
        lower.contains("meeting") || lower.contains("call") || lower.contains("match") || lower.contains("football") -> "meeting"
        else -> null
    }
}
