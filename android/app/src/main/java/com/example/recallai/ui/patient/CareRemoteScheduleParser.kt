package com.example.recallai.ui.patient

import com.example.recallai.data.remote.RemoteMemoryDto
import com.example.recallai.ui.screens.ScheduleSlotItem
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Parses caregiver-synced care tasks from remote memory entries tagged [CARE_TASK].
 */
object CareRemoteScheduleParser {

    private val time12: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

    /**
     * Care tasks scheduled for "today" on the patient device (same rules as [slotsFromRemote]).
     * Used for dashboard counts and push-style notifications when new ids appear.
     */
    fun todayCareTasks(
        remote: List<RemoteMemoryDto>,
        zone: ZoneId,
        excludeTitles: Set<String> = emptySet()
    ): List<Pair<String, String>> {
        val dayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = dayStart + 24L * 60L * 1000L
        val out = mutableListOf<Pair<String, String>>()
        val seen = mutableSetOf<String>()
        for (dto in remote) {
            val raw = (dto.rawText ?: dto.text ?: "").trim()
            if (!raw.contains("[CARE_TASK]")) continue
            val title = Regex("(?m)^Title:\\s*(.+)$").find(raw)?.groupValues?.get(1)?.trim() ?: continue
            if (!PatientDashboardVisibility.isCareRemoteSlotVisible(title, excludeTitles)) continue
            val timeLine = Regex("(?m)^Time:\\s*(.+)$").find(raw)?.groupValues?.get(1)?.trim().orEmpty()
            val dueLine = Regex("(?m)^Due:\\s*(.+)$").find(raw)?.groupValues?.get(1)?.trim().orEmpty()
            val createdMs = parseCreatedMs(dto.createdAt)
            val sortEpoch = when {
                timeLine.isNotBlank() -> combineTodayWithTime(timeLine, zone) ?: createdMs
                else -> createdMs
            }
            val include =
                dueLine.contains("today", ignoreCase = true) ||
                    dueLine.contains("tonight", ignoreCase = true) ||
                    sortEpoch in dayStart until dayEnd ||
                    createdMs in dayStart until dayEnd
            if (!include) continue
            val stableId = dto._id?.takeIf { it.isNotBlank() } ?: "h:${raw.hashCode()}"
            if (!seen.add(stableId)) continue
            out.add(stableId to title)
        }
        return out
    }

    fun slotsFromRemote(
        remote: List<RemoteMemoryDto>,
        zone: ZoneId,
        excludeTitles: Set<String> = emptySet()
    ): List<Pair<Long, ScheduleSlotItem>> {
        val dayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = dayStart + 24L * 60L * 60L * 1000L
        val out = mutableListOf<Pair<Long, ScheduleSlotItem>>()
        val seen = mutableSetOf<String>()
        for (dto in remote) {
            val raw = (dto.rawText ?: dto.text ?: "").trim()
            if (!raw.contains("[CARE_TASK]")) continue
            val stableId = dto._id?.takeIf { it.isNotBlank() } ?: "h:${raw.hashCode()}"
            if (!seen.add(stableId)) continue
            val title = Regex("(?m)^Title:\\s*(.+)$").find(raw)?.groupValues?.get(1)?.trim() ?: continue
            if (!PatientDashboardVisibility.isCareRemoteSlotVisible(title, excludeTitles)) continue
            val timeLine = Regex("(?m)^Time:\\s*(.+)$").find(raw)?.groupValues?.get(1)?.trim().orEmpty()
            val dueLine = Regex("(?m)^Due:\\s*(.+)$").find(raw)?.groupValues?.get(1)?.trim().orEmpty()
            val createdMs = parseCreatedMs(dto.createdAt)
            val sortEpoch = when {
                timeLine.isNotBlank() -> combineTodayWithTime(timeLine, zone) ?: createdMs
                else -> createdMs
            }
            val include =
                dueLine.contains("today", ignoreCase = true) ||
                    dueLine.contains("tonight", ignoreCase = true) ||
                    sortEpoch in dayStart until dayEnd ||
                    createdMs in dayStart until dayEnd
            if (!include) continue

            val timeLabel = when {
                timeLine.isNotBlank() ->
                    runCatching {
                        val lt = LocalTime.parse(timeLine.trim(), time12)
                        time12.format(LocalDate.now(zone).atTime(lt))
                    }.getOrElse { formatEpoch(sortEpoch, zone) }
                else -> formatEpoch(sortEpoch, zone)
            }
            val display = if (dueLine.isNotBlank() && !dueLine.equals("Today", ignoreCase = true)) {
                "$title · $dueLine"
            } else {
                title
            }
            out.add(
                sortEpoch to ScheduleSlotItem(
                    timeLabel = timeLabel,
                    title = "Care: $display",
                    kind = "care_task"
                )
            )
        }
        return out
    }

    private fun parseCreatedMs(iso: String?): Long {
        if (iso.isNullOrBlank()) return System.currentTimeMillis()
        return runCatching { Instant.parse(iso).toEpochMilli() }.getOrElse {
            runCatching { OffsetDateTime.parse(iso).toInstant().toEpochMilli() }
                .getOrDefault(System.currentTimeMillis())
        }
    }

    private fun combineTodayWithTime(timeLine: String, zone: ZoneId): Long? {
        val t = timeLine.trim()
        return runCatching {
            val lt = LocalTime.parse(t, time12)
            LocalDate.now(zone).atTime(lt).atZone(zone).toInstant().toEpochMilli()
        }.getOrNull()
    }

    private fun formatEpoch(epoch: Long, zone: ZoneId): String {
        val dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(epoch), zone)
        return time12.format(dt)
    }
}
