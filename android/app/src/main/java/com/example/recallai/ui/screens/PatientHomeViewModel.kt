package com.example.recallai.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.example.recallai.data.AuthManager
import com.example.recallai.data.CareRepository
import com.example.recallai.data.MemoryRepository
import com.example.recallai.data.MoodCheckInRepository
import com.example.recallai.data.PatientAlarmRepository
import com.example.recallai.data.RemoteMemoryRepository
import com.example.recallai.data.local.MemoryEntity
import com.example.recallai.data.local.PatientAlarmEntity
import com.example.recallai.data.local.PatientAlarmRepeatMode
import com.example.recallai.data.local.ReminderEntity
import com.example.recallai.data.local.ReminderRepeatMode
import com.example.recallai.data.local.ReminderStatus
import com.example.recallai.reminders.PatientAlarmScheduleHelper
import com.example.recallai.reminders.ReminderRepository
import com.example.recallai.notifications.RecallNotifications
import com.example.recallai.reminders.CareTaskNotifier
import com.example.recallai.reminders.ReminderScheduleHelper
import com.example.recallai.reminders.ReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import com.example.recallai.ui.patient.CareRemoteScheduleParser
import com.example.recallai.ui.patient.EventRemoteScheduleParser
import com.example.recallai.ui.patient.PatientDashboardVisibility
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import javax.inject.Inject
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ScheduleSlotItem(
    val timeLabel: String,
    val title: String,
    val kind: String
)

data class UpcomingScheduleItem(
    val periodLabel: String,
    val timeLabel: String,
    val title: String,
    val kind: String
)

data class PatientAssignedCareTask(
    val id: String,
    val title: String,
    val description: String?,
    val priority: String,
    val caregiverName: String,
    val dueLabel: String,
    val isDone: Boolean
)

data class PatientHomeUiState(
    val totalMemories: Int = 0,
    val chatCount: Int = 0,
    val voiceCount: Int = 0,
    val medicationLogs: Int = 0,
    val routineLogs: Int = 0,
    val emergencyEvents: Int = 0,
    val objectDetections: Int = 0,
    val faceInsights: Int = 0,
    val recent: List<MemoryEntity> = emptyList(),
    val isLoading: Boolean = false,
    val lastDashboardSyncMs: Long? = null,
    val patientAlarms: List<PatientAlarmEntity> = emptyList(),
    val scheduleMorning: List<ScheduleSlotItem> = emptyList(),
    val scheduleAfternoon: List<ScheduleSlotItem> = emptyList(),
    val scheduleEvening: List<ScheduleSlotItem> = emptyList(),
    val upcomingTomorrow: List<UpcomingScheduleItem> = emptyList(),
    /** Caregiver-assigned care tasks counted for today (dashboard + notifications). */
    val careTasksToday: Int = 0,
    val assignedCareTasks: List<PatientAssignedCareTask> = emptyList(),
    val todayMood: String? = null
)

@HiltViewModel
class PatientHomeViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val memoryRepository: MemoryRepository,
    private val reminderRepository: ReminderRepository,
    private val patientAlarmRepository: PatientAlarmRepository,
    private val remoteMemoryRepository: RemoteMemoryRepository,
    private val careRepository: CareRepository,
    private val moodCheckInRepository: MoodCheckInRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PatientHomeUiState(isLoading = true))
    val uiState = _uiState.asStateFlow()

    private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")
    private var lastMemorySyncMs: Long = 0L

    init {
        viewModelScope.launch {
            moodCheckInRepository.observeToday().collect { entity ->
                _uiState.value = _uiState.value.copy(todayMood = entity?.mood)
            }
        }
        viewModelScope.launch {
            combine(
                memoryRepository.observeRecentMemories(24),
                reminderRepository.remindersFlow,
                patientAlarmRepository.alarms
            ) { _, reminders, alarms ->
                reminders to alarms
            }
                .debounce(250L)
                .collect { (reminders, alarms) ->
                    refreshDashboard(reminders, alarms)
                }
        }
        viewModelScope.launch {
            if (AuthManager.userRole != "patient") return@launch
            while (isActive) {
                delay(45_000)
                refresh()
            }
        }
    }

    fun submitMood(mood: String) {
        viewModelScope.launch {
            val normalized = when (mood) {
                "excited" -> "happy"
                else -> mood
            }
            moodCheckInRepository.submitMood(normalized)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val reminders = reminderRepository.remindersFlow.first()
            val alarms = patientAlarmRepository.alarms.first()
            refreshDashboard(reminders, alarms)
        }
    }

    fun markAssignedCareTaskDone(taskId: String) {
        viewModelScope.launch {
            val taskTitle = _uiState.value.assignedCareTasks.find { it.id == taskId }?.title ?: "Task"
            _uiState.value = _uiState.value.copy(
                assignedCareTasks = _uiState.value.assignedCareTasks.filter { it.id != taskId }
            )
            val result = runCatching { careRepository.markCareTaskDone(taskId) }
            result.onSuccess {
                RecallNotifications.show(
                    context = appContext,
                    kind = RecallNotifications.Kind.CareTask,
                    title = "Task completed",
                    body = "You marked \"$taskTitle\" as done. Your caregiver was notified.",
                    ignoreActivityToggle = true
                )
            }
            val reminders = reminderRepository.remindersFlow.first()
            val alarms = patientAlarmRepository.alarms.first()
            refreshDashboard(reminders, alarms)
        }
    }

    fun addAlarm(
        label: String,
        hour: Int,
        minute: Int,
        mode: PatientAlarmRepeatMode,
        daysMask: Int,
        oneOffTriggerAtMs: Long? = null
    ) {
        viewModelScope.launch {
            if (mode == PatientAlarmRepeatMode.WEEKLY && daysMask == 0) return@launch
            val trimmed = label.trim().ifBlank { "Alarm" }
            val next = when (mode) {
                PatientAlarmRepeatMode.ONCE ->
                    oneOffTriggerAtMs ?: computeOnceTriggerMillis(hour, minute)
                PatientAlarmRepeatMode.DAILY,
                PatientAlarmRepeatMode.WEEKLY -> 0L
            }
            val draft = PatientAlarmEntity(
                label = trimmed,
                hour = hour,
                minute = minute,
                repeatMode = mode,
                daysOfWeekMask = if (mode == PatientAlarmRepeatMode.WEEKLY) daysMask else 0,
                enabled = true,
                nextTriggerAt = next
            )
            patientAlarmRepository.save(draft)
            RecallNotifications.show(
                context = appContext,
                kind = RecallNotifications.Kind.Reminder,
                title = "Alarm scheduled",
                body = "$trimmed at ${timeFmt.format(LocalDateTime.of(LocalDate.now(), LocalTime.of(hour, minute)))}.",
                ignoreActivityToggle = true
            )
        }
    }

    fun deleteAlarm(alarm: PatientAlarmEntity) {
        viewModelScope.launch {
            patientAlarmRepository.delete(alarm)
        }
    }

    fun addReminder(
        preset: String,
        note: String,
        atMillis: Long,
        repeatMode: ReminderRepeatMode = ReminderRepeatMode.NONE,
        daysMask: Int = 0
    ) {
        viewModelScope.launch {
            val entity = ReminderEntity(
                title = preset,
                description = note.trim().takeIf { it.isNotBlank() },
                datetime = atMillis,
                status = ReminderStatus.PENDING,
                source = "patient",
                warn10Min = false,
                preset = preset,
                repeatMode = repeatMode,
                daysOfWeekMask = daysMask
            )
            val id = reminderRepository.add(entity)
            val saved = entity.copy(id = id)
            ReminderScheduler.schedule(reminderRepository.context(), saved)
            RecallNotifications.show(
                context = appContext,
                kind = RecallNotifications.Kind.Reminder,
                title = "Reminder scheduled",
                body = "$preset is set for ${formatReminderWhen(atMillis)}.",
                ignoreActivityToggle = false
            )
        }
    }

    private fun formatReminderWhen(atMillis: Long): String {
        val dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(atMillis), ZoneId.systemDefault())
        return timeFmt.format(dt)
    }

    private suspend fun refreshDashboard(
        reminders: List<ReminderEntity>,
        alarms: List<PatientAlarmEntity>
    ) {
        val wallMs = measureTimeMillis {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val now = System.currentTimeMillis()
            if (now - lastMemorySyncMs > 60_000L) {
                memoryRepository.syncFromServer()
                lastMemorySyncMs = now
            }

            val total = memoryRepository.countAllMemories()
            val chat = memoryRepository.countByType("CHATBOT")
            val voice =
                memoryRepository.countByType("VOICE_CHATBOT") + memoryRepository.countByType("VOICE")
            val medicationLogs = memoryRepository.countByType("MEDICATION_LOG")
            val routineLogs = memoryRepository.countByType("ROUTINE_LOG")
            val emergencyEvents = memoryRepository.countByType("EMERGENCY_EVENT")
            val objectDetections = memoryRepository.countByType("OBJECT_DETECTION")
            val faceInsights = memoryRepository.countByType("FACE_ANALYSIS")
            val recent = memoryRepository.getRecentMemories(limit = 3)

            val zone = ZoneId.systemDefault()
            val remoteList =
                if (AuthManager.userRole == "patient") {
                    runCatching {
                        remoteMemoryRepository.getMemories()
                    }.getOrDefault(emptyList())
                } else {
                    emptyList()
                }

            val (allAssignedCareTasks, serverPendingCount) =
                if (AuthManager.userRole == "patient") {
                    val raw = runCatching { careRepository.careTasksForPatient() }.getOrDefault(emptyList())
                    val sorted = raw.sortedWith(
                        compareBy(
                            {
                                when (it.priority?.uppercase()) {
                                    "HIGH" -> 0
                                    "LOW" -> 2
                                    else -> 1
                                }
                            },
                            { it.dueAt ?: "" }
                        )
                    )
                    val rows = sorted.map { dto ->
                        val done =
                            dto.status?.equals("done", ignoreCase = true) == true ||
                                !dto.doneAt.isNullOrBlank()
                        PatientAssignedCareTask(
                            id = dto._id,
                            title = dto.title,
                            description = dto.description,
                            priority = dto.priority?.uppercase() ?: "MEDIUM",
                            caregiverName = dto.caregiverName?.trim()?.takeIf { it.isNotEmpty() } ?: "Caregiver",
                            dueLabel = formatCareTaskDue(dto.dueAt, zone),
                            isDone = done
                        )
                    }
                    rows to rows.count { !it.isDone }
                } else {
                    emptyList<PatientAssignedCareTask>() to 0
                }

            val assignedCareTasks = PatientDashboardVisibility.visibleCareTasks(allAssignedCareTasks)
            val doneCareTitles = PatientDashboardVisibility.doneCareTaskTitles(allAssignedCareTasks)

            val careExtras =
                if (AuthManager.userRole == "patient") {
                    CareRemoteScheduleParser.slotsFromRemote(remoteList, zone, doneCareTitles)
                } else {
                    emptyList()
                }

            val todayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
            val todayEnd = todayStart + 24L * 60L * 60L * 1000L
            val tomorrowStart = todayEnd
            val tomorrowEnd = tomorrowStart + 24L * 60L * 60L * 1000L
            val eventExtrasToday =
                if (AuthManager.userRole == "patient") {
                    EventRemoteScheduleParser.slotsForDay(remoteList, zone, todayStart, todayEnd)
                } else {
                    emptyList()
                }
            val eventExtrasTomorrow =
                if (AuthManager.userRole == "patient") {
                    EventRemoteScheduleParser.slotsForDay(remoteList, zone, tomorrowStart, tomorrowEnd)
                } else {
                    emptyList()
                }

            val careTasksToday =
                if (AuthManager.userRole == "patient") {
                    val pairs = CareRemoteScheduleParser.todayCareTasks(remoteList, zone, doneCareTitles)
                    CareTaskNotifier.notifyNewTasks(appContext, pairs)
                    pairs.size + serverPendingCount
                } else {
                    0
                }

            val visibleAlarms = PatientDashboardVisibility.visibleAlarms(alarms, todayStart, now)
            val (morning, afternoon, evening) =
                buildSchedule(
                    reminders = reminders,
                    alarms = visibleAlarms,
                    remoteExtras = careExtras + eventExtrasToday,
                    dayStartMs = todayStart,
                    dayEndMs = todayEnd,
                    nowMs = now,
                    hidePastSlots = true
                )
            val (tomMorning, tomAfternoon, tomEvening) =
                buildSchedule(
                    reminders = reminders,
                    alarms = visibleAlarms,
                    remoteExtras = eventExtrasTomorrow,
                    dayStartMs = tomorrowStart,
                    dayEndMs = tomorrowEnd,
                    nowMs = now,
                    hidePastSlots = false
                )
            val upcomingTomorrow = buildUpcomingTomorrow(tomMorning, tomAfternoon, tomEvening)

            _uiState.value = PatientHomeUiState(
                totalMemories = total,
                chatCount = chat,
                voiceCount = voice,
                medicationLogs = medicationLogs,
                routineLogs = routineLogs,
                emergencyEvents = emergencyEvents,
                objectDetections = objectDetections,
                faceInsights = faceInsights,
                recent = recent,
                isLoading = false,
                lastDashboardSyncMs = null,
                patientAlarms = visibleAlarms,
                scheduleMorning = morning,
                scheduleAfternoon = afternoon,
                scheduleEvening = evening,
                upcomingTomorrow = upcomingTomorrow,
                careTasksToday = careTasksToday,
                assignedCareTasks = assignedCareTasks
            )
        }
        _uiState.value = _uiState.value.copy(lastDashboardSyncMs = wallMs)
    }

    private fun formatCareTaskDue(iso: String?, zone: ZoneId): String {
        if (iso.isNullOrBlank()) return "Anytime"
        return runCatching {
            val inst = Instant.parse(iso)
            val dt = LocalDateTime.ofInstant(inst, zone)
            val day = LocalDate.now(zone)
            val taskDay = dt.toLocalDate()
            val prefix = when (taskDay) {
                day -> "Today"
                day.plusDays(1) -> "Tomorrow"
                else -> taskDay.format(DateTimeFormatter.ofPattern("MMM d"))
            }
            "$prefix · ${timeFmt.format(dt)}"
        }.getOrElse { iso }
    }

    private fun buildSchedule(
        reminders: List<ReminderEntity>,
        alarms: List<PatientAlarmEntity>,
        remoteExtras: List<Pair<Long, ScheduleSlotItem>>,
        dayStartMs: Long,
        dayEndMs: Long,
        nowMs: Long = System.currentTimeMillis(),
        hidePastSlots: Boolean = true
    ): Triple<List<ScheduleSlotItem>, List<ScheduleSlotItem>, List<ScheduleSlotItem>> {
        val zone = ZoneId.systemDefault()
        val items = mutableListOf<Pair<Long, ScheduleSlotItem>>()

        reminders.forEach { r ->
            val epoch: Long = when (r.repeatMode) {
                ReminderRepeatMode.NONE ->
                    if (r.datetime in dayStartMs until dayEndMs) r.datetime else return@forEach
                ReminderRepeatMode.DAILY -> {
                    val (h, m) = ReminderScheduleHelper.hourMinuteFromEpoch(r.datetime)
                    val t = ReminderScheduleHelper.combineLocalDateAndTime(dayStartMs, h, m)
                    if (t in dayStartMs until dayEndMs) t else return@forEach
                }
                ReminderRepeatMode.WEEKLY -> {
                    val dayBit = LocalDateTime.ofInstant(Instant.ofEpochMilli(dayStartMs), zone).dayOfWeek.value - 1
                    if ((r.daysOfWeekMask shr dayBit) and 1 == 0) return@forEach
                    val (h, m) = ReminderScheduleHelper.hourMinuteFromEpoch(r.datetime)
                    val t = ReminderScheduleHelper.combineLocalDateAndTime(dayStartMs, h, m)
                    if (t in dayStartMs until dayEndMs) t else return@forEach
                }
            }
            if (hidePastSlots &&
                !PatientDashboardVisibility.isReminderVisibleInSchedule(r, dayStartMs, dayEndMs, epoch, nowMs)
            ) {
                return@forEach
            }
            if (!hidePastSlots && r.status != ReminderStatus.PENDING) {
                return@forEach
            }
            val label = when {
                !r.description.isNullOrBlank() -> "${r.title} · ${r.description}"
                else -> r.title
            }
            items.add(
                epoch to ScheduleSlotItem(
                    timeLabel = formatTime(epoch, zone),
                    title = label,
                    kind = "reminder"
                )
            )
        }

        alarms.forEach { a ->
            val occ = PatientAlarmScheduleHelper.occurrencesToday(a, dayStartMs, dayEndMs)
            if (occ != null && (!hidePastSlots || PatientDashboardVisibility.shouldShowOnTodayTimeline(occ, nowMs))) {
                items.add(
                    occ to ScheduleSlotItem(
                        timeLabel = formatTime(occ, zone),
                        title = a.label,
                        kind = "alarm"
                    )
                )
            }
        }

        remoteExtras.forEach { (epoch, slot) ->
            if (!hidePastSlots || PatientDashboardVisibility.shouldShowOnTodayTimeline(epoch, nowMs)) {
                if (!isDuplicateSlot(items, epoch, slot.title)) {
                    items.add(epoch to slot)
                }
            }
        }

        val sorted = items.sortedBy { it.first }
        val morning = mutableListOf<ScheduleSlotItem>()
        val afternoon = mutableListOf<ScheduleSlotItem>()
        val evening = mutableListOf<ScheduleSlotItem>()
        sorted.forEach { (epoch, slot) ->
            val hour = LocalDateTime.ofInstant(Instant.ofEpochMilli(epoch), zone).hour
            when {
                hour < 12 -> morning.add(slot)
                hour < 17 -> afternoon.add(slot)
                else -> evening.add(slot)
            }
        }

        return Triple(morning, afternoon, evening)
    }

    private fun buildUpcomingTomorrow(
        morning: List<ScheduleSlotItem>,
        afternoon: List<ScheduleSlotItem>,
        evening: List<ScheduleSlotItem>
    ): List<UpcomingScheduleItem> = buildList {
        morning.forEach { add(UpcomingScheduleItem("Morning", it.timeLabel, it.title, it.kind)) }
        afternoon.forEach { add(UpcomingScheduleItem("Afternoon", it.timeLabel, it.title, it.kind)) }
        evening.forEach { add(UpcomingScheduleItem("Evening", it.timeLabel, it.title, it.kind)) }
    }

    private fun isDuplicateSlot(
        existing: List<Pair<Long, ScheduleSlotItem>>,
        epoch: Long,
        title: String
    ): Boolean {
        val key = title.trim().lowercase()
        if (key.isBlank()) return false
        return existing.any { (e, slot) ->
            kotlin.math.abs(e - epoch) < 120_000L &&
                (slot.title.trim().lowercase().contains(key) || key.contains(slot.title.trim().lowercase()))
        }
    }

    private fun formatTime(epochMs: Long, zone: ZoneId): String {
        val dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), zone)
        return timeFmt.format(dt)
    }

    private fun computeOnceTriggerMillis(hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return cal.timeInMillis
    }
}
