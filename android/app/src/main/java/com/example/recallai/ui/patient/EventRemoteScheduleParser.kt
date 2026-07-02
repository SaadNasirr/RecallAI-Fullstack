package com.example.recallai.ui.patient

import com.example.recallai.data.remote.RemoteMemoryDto
import com.example.recallai.reminders.ReminderNlp
import com.example.recallai.ui.screens.ScheduleSlotItem
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Turns therapist-chat event memories (and other time-stamped remote entries) into schedule slots.
 */
object EventRemoteScheduleParser {

    private val time12: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
    private val eventTimeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val leadingTimestamp = Regex("""^\[[^\]]+\]\s*""")
    private val bracketTimestamp = Regex("""^\[([^\]]+)\]""")

    fun slotsForDay(
        remote: List<RemoteMemoryDto>,
        zone: ZoneId,
        dayStartMs: Long,
        dayEndMs: Long
    ): List<Pair<Long, ScheduleSlotItem>> {
        val out = mutableListOf<Pair<Long, ScheduleSlotItem>>()
        val seen = mutableSetOf<String>()
        for (dto in remote) {
            val stableId = dto._id?.takeIf { it.isNotBlank() } ?: continue
            if (!seen.add(stableId)) continue

            val raw = (dto.rawText ?: dto.text ?: "").trim()
            if (raw.isBlank() || raw.contains("[CARE_TASK]")) continue

            val type = dto.type?.lowercase(Locale.US).orEmpty()
            val isEvent = type == "event" || type == "conversation"
            if (!isEvent && dto.eventTime.isNullOrBlank()) continue

            val epoch =
                parseEventTimeMs(dto.eventTime, zone)
                    ?: parseBracketTimestamp(raw, zone)
                    ?: parseFromText(raw)
                    ?: continue
            if (epoch !in dayStartMs until dayEndMs) continue

            val title = displayTitle(raw).ifBlank { "Event" }
            out.add(
                epoch to ScheduleSlotItem(
                    timeLabel = formatEpoch(epoch, zone),
                    title = title,
                    kind = "event"
                )
            )
        }
        return out
    }

    private fun parseBracketTimestamp(raw: String, zone: ZoneId): Long? {
        val inner = bracketTimestamp.find(raw)?.groupValues?.get(1)?.trim() ?: return null
        return parseEventTimeMs(inner, zone)
    }

    private fun parseFromText(raw: String): Long? {
        val stripped = leadingTimestamp.replace(raw, "").trim()
        val draft = ReminderNlp.tryParseCompleteReminder(stripped) ?: return null
        return draft.datetime
    }

    private fun parseEventTimeMs(iso: String?, zone: ZoneId): Long? {
        if (iso.isNullOrBlank()) return null
        val trimmed = iso.trim()
        return runCatching {
            LocalDateTime.parse(trimmed, eventTimeFmt).atZone(zone).toInstant().toEpochMilli()
        }.getOrElse {
            runCatching { Instant.parse(trimmed).toEpochMilli() }.getOrElse {
                runCatching { OffsetDateTime.parse(trimmed).toInstant().toEpochMilli() }.getOrNull()
            }
        }
    }

    private fun displayTitle(raw: String): String {
        val stripped = leadingTimestamp.replace(raw, "").trim()
        val meeting = Regex("""meeting(?:\s+with\s+[^.]+)?""", RegexOption.IGNORE_CASE).find(stripped)?.value
        if (!meeting.isNullOrBlank()) return meeting.replaceFirstChar { it.uppercase() }
        val appointment = Regex("""appointment(?:\s+with\s+[^.]+)?""", RegexOption.IGNORE_CASE).find(stripped)?.value
        if (!appointment.isNullOrBlank()) return appointment.replaceFirstChar { it.uppercase() }
        return stripped.take(60)
    }

    private fun formatEpoch(epoch: Long, zone: ZoneId): String {
        val dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(epoch), zone)
        return time12.format(dt)
    }
}
