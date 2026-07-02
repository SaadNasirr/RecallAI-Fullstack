package com.example.recallai.ui.screens



import androidx.lifecycle.ViewModel

import androidx.lifecycle.viewModelScope

import com.example.recallai.data.CareRepository

import com.example.recallai.data.CaregiverRulesRepository

import com.example.recallai.data.MemoryRepository

import com.example.recallai.data.remote.CareAlertDto

import com.example.recallai.data.local.MemoryEntity

import dagger.hilt.android.lifecycle.HiltViewModel

import kotlinx.coroutines.flow.MutableStateFlow

import kotlinx.coroutines.flow.asStateFlow

import kotlinx.coroutines.flow.debounce

import kotlinx.coroutines.delay

import kotlinx.coroutines.isActive

import kotlinx.coroutines.launch

import javax.inject.Inject

import kotlin.system.measureTimeMillis



enum class CaregiverTriggerSeverity { HIGH, MEDIUM, INFO }

enum class CaregiverTriggerAction { OPEN_RULES, OPEN_TIMELINE, OPEN_ZONES }



data class CaregiverAlertTrigger(

    val message: String,

    val severity: CaregiverTriggerSeverity,

    val iconType: CaregiverTriggerAction,

    val action: CaregiverTriggerAction,

    val actionLabel: String

)



data class CaregiverHomeUiState(

    val totalMemories: Int = 0,

    val chatMemories: Int = 0,

    val medicationLogs: Int = 0,

    val medicationAlerts: Int = 0,

    val knownPeople: Int = 0,

    val pendingCareTasks: Int = 0,

    val riskAlerts: Int = 0,

    val careInsights: List<String> = emptyList(),

    val trendInsights: List<String> = emptyList(),

    val alertBreakdown: List<CaregiverAlertTrigger> = emptyList(),

    val recent: List<MemoryEntity> = emptyList(),

    val isLoading: Boolean = false,

    /** Wall-clock time to rebuild dashboard after DB change (latency check). */

    val lastDashboardSyncMs: Long? = null,

    /** Latest patient mood line from care alerts (when synced). */

    val latestMoodLine: String? = null,

    /** Unread items from GET /care/alerts/inbox (real patient→caregiver alerts). */

    val careAlertsUnread: Int = 0,

    /** Recent care alerts for dashboard preview. */

    val recentCareAlerts: List<CareAlertDto> = emptyList()

)



@HiltViewModel

class CaregiverHomeViewModel @Inject constructor(

    private val memoryRepository: MemoryRepository,

    private val rulesRepository: CaregiverRulesRepository,

    private val careRepository: CareRepository

) : ViewModel() {



    private val _uiState = MutableStateFlow(CaregiverHomeUiState(isLoading = true))

    val uiState = _uiState.asStateFlow()



    init {

        viewModelScope.launch {

            memoryRepository.observeRecentMemories(80)

                .debounce(320L)

                .collect {

                    refreshDashboard()

                }

        }

        viewModelScope.launch {

            while (isActive) {

                delay(25_000)

                refreshDashboard()

            }

        }

    }



    fun refresh() {

        viewModelScope.launch { refreshDashboard() }

    }



    private suspend fun refreshDashboard() {

        val wallMs = measureTimeMillis {

            _uiState.value = _uiState.value.copy(isLoading = true)

            runCatching { careRepository.ensureDefaultPatientSelected() }

            runCatching { memoryRepository.syncFromServer() }

            val careAlerts = runCatching { careRepository.alertsInbox() }.getOrDefault(emptyList())

            val careAlertsUnread = careAlerts.count { it.unread == true }

            val recentCareAlerts = careAlerts

                .sortedByDescending { it.createdAt.orEmpty() }

                .take(5)

            val total = memoryRepository.countAllMemories()

            val chat = memoryRepository.countByType("CHATBOT") +

                memoryRepository.countByType("VOICE_CHATBOT")

            val geofenceAlerts = memoryRepository.countByType("GEOFENCE_ALERT")

            val medicationLogs = memoryRepository.countByType("MEDICATION_LOG")

            val medicationAlerts = memoryRepository.countByType("MEDICATION_ALERT")

            val knownPeople = memoryRepository.countByType("PEOPLE_MEMORY")

            val routineLogs = memoryRepository.countByType("ROUTINE_LOG")

            val pendingCareTasks = run {
                val pid = careRepository.selectedPatientId.value
                if (pid.isNullOrBlank()) {
                    0
                } else {
                    runCatching {
                        careRepository.careTasksForCaregiver(pid).count { dto ->
                            dto.status?.equals("done", ignoreCase = true) != true &&
                                dto.doneAt.isNullOrBlank()
                        }
                    }.getOrDefault(0)
                }
            }

            val recent = memoryRepository.getRecentMemories(limit = 4)

            val rules = rulesRepository.getRules()

            val alerts = estimateRiskAlerts(

                chatCount = chat,

                total = total,

                inactivityDaysThreshold = rules.inactivityDays,

                riskThresholdPercent = rules.riskThresholdPercent,

                recent = recent,

                geofenceAlerts = geofenceAlerts

            )

            val breakdown = buildAlertBreakdown(

                chatCount = chat,

                total = total,

                inactivityDaysThreshold = rules.inactivityDays,

                riskThresholdPercent = rules.riskThresholdPercent,

                recent = recent,

                geofenceAlerts = geofenceAlerts

            )

            val insights = buildCareInsights(

                medicationLogs = medicationLogs,

                medicationAlerts = medicationAlerts,

                knownPeople = knownPeople,

                geofenceAlerts = geofenceAlerts,

                total = total,

                routineLogs = routineLogs,

                pendingCareTasks = pendingCareTasks

            )

            val trends = buildTrendInsights()

            val moodLine = careAlerts

                .filter { it.type == "mood_checkin" }

                .maxByOrNull { it.createdAt ?: "" }

                ?.body

            _uiState.value = CaregiverHomeUiState(

                totalMemories = total,

                chatMemories = chat,

                medicationLogs = medicationLogs,

                medicationAlerts = medicationAlerts,

                knownPeople = knownPeople,

                pendingCareTasks = pendingCareTasks,

                riskAlerts = alerts,

                careInsights = insights,

                trendInsights = trends,

                alertBreakdown = breakdown,

                recent = recent,

                isLoading = false,

                lastDashboardSyncMs = null,

                latestMoodLine = moodLine,

                careAlertsUnread = careAlertsUnread,

                recentCareAlerts = recentCareAlerts

            )

        }

        _uiState.value = _uiState.value.copy(lastDashboardSyncMs = wallMs)

    }



    private fun estimateRiskAlerts(

        chatCount: Int,

        total: Int,

        inactivityDaysThreshold: Int,

        riskThresholdPercent: Int,

        recent: List<MemoryEntity>,

        geofenceAlerts: Int

    ): Int {

        if (total == 0) return 0

        val chatRatioPercent = ((chatCount.toFloat() / total.toFloat()) * 100f).toInt()

        var alerts = 0

        if (chatRatioPercent >= riskThresholdPercent) alerts++



        val latest = recent.maxByOrNull { it.createdAt }

        val daysSinceLast = if (latest == null) Int.MAX_VALUE else {

            val deltaMs = System.currentTimeMillis() - latest.createdAt

            (deltaMs / (1000L * 60L * 60L * 24L)).toInt()

        }

        if (daysSinceLast >= inactivityDaysThreshold) alerts++

        if (geofenceAlerts > 0) alerts++

        if (total < 3) alerts++

        return alerts.coerceIn(0, 3)

    }



    private fun buildAlertBreakdown(

        chatCount: Int,

        total: Int,

        inactivityDaysThreshold: Int,

        riskThresholdPercent: Int,

        recent: List<MemoryEntity>,

        geofenceAlerts: Int

    ): List<CaregiverAlertTrigger> {

        if (total == 0) return emptyList()

        val items = mutableListOf<CaregiverAlertTrigger>()

        val chatRatioPercent = ((chatCount.toFloat() / total.toFloat()) * 100f).toInt()

        if (chatRatioPercent >= riskThresholdPercent) {

            items += CaregiverAlertTrigger(

                message = "High risk score: $chatRatioPercent% >= $riskThresholdPercent%",

                severity = CaregiverTriggerSeverity.HIGH,

                iconType = CaregiverTriggerAction.OPEN_RULES,

                action = CaregiverTriggerAction.OPEN_RULES,

                actionLabel = "Open Rules"

            )

        }



        val latest = recent.maxByOrNull { it.createdAt }

        val daysSinceLast = if (latest == null) Int.MAX_VALUE else {

            val deltaMs = System.currentTimeMillis() - latest.createdAt

            (deltaMs / (1000L * 60L * 60L * 24L)).toInt()

        }

        if (daysSinceLast >= inactivityDaysThreshold) {

            items += CaregiverAlertTrigger(

                message = "Inactivity: $daysSinceLast days >= $inactivityDaysThreshold days",

                severity = CaregiverTriggerSeverity.MEDIUM,

                iconType = CaregiverTriggerAction.OPEN_TIMELINE,

                action = CaregiverTriggerAction.OPEN_TIMELINE,

                actionLabel = "Open Timeline"

            )

        }

        if (geofenceAlerts > 0) {

            items += CaregiverAlertTrigger(

                message = "Geofence: $geofenceAlerts recent out-of-zone alerts",

                severity = CaregiverTriggerSeverity.HIGH,

                iconType = CaregiverTriggerAction.OPEN_ZONES,

                action = CaregiverTriggerAction.OPEN_ZONES,

                actionLabel = "Open Zones"

            )

        }

        if (total < 3) {

            items += CaregiverAlertTrigger(

                message = "Low data confidence: only $total memories logged",

                severity = CaregiverTriggerSeverity.INFO,

                iconType = CaregiverTriggerAction.OPEN_TIMELINE,

                action = CaregiverTriggerAction.OPEN_TIMELINE,

                actionLabel = "Open Timeline"

            )

        }

        return items.sortedBy {

            when (it.severity) {

                CaregiverTriggerSeverity.HIGH -> 0

                CaregiverTriggerSeverity.MEDIUM -> 1

                CaregiverTriggerSeverity.INFO -> 2

            }

        }

    }



    private fun buildCareInsights(

        medicationLogs: Int,

        medicationAlerts: Int,

        knownPeople: Int,

        geofenceAlerts: Int,

        total: Int,

        routineLogs: Int,

        pendingCareTasks: Int

    ): List<String> {

        val list = mutableListOf<String>()

        list += if (medicationLogs > 0) {

            "Medication adherence check-ins logged: $medicationLogs."

        } else {

            "No medication adherence logs yet. Encourage routine check-ins."

        }

        list += if (knownPeople > 0) {

            "People memory entries available: $knownPeople."

        } else {

            "People memory book is empty. Add key family/caregiver identities."

        }

        if (geofenceAlerts > 0) {

            list += "Geofence alerts detected recently: $geofenceAlerts."

        }

        if (medicationAlerts > 0) {

            list += "Medication escalations pending review: $medicationAlerts."

        }

        list += if (routineLogs > 0) {

            "Routine completion logs available: $routineLogs."

        } else {

            "No routine logs yet. Encourage daily anchor check-ins."

        }

        if (pendingCareTasks > 0) {

            list += "Open caregiver tasks: $pendingCareTasks."

        }

        if (total >= 10) {

            list += "Good data volume for trend interpretation."

        }

        return list

    }



    private suspend fun buildTrendInsights(): List<String> {

        val now = System.currentTimeMillis()

        val weekAgo = now - (7L * 24L * 60L * 60L * 1000L)

        val recent = memoryRepository.getRecentMemories(limit = 120).filter { it.createdAt >= weekAgo }

        if (recent.isEmpty()) return listOf("Not enough data for 7-day trends yet.")



        val medicationTaken = recent.count { it.type == "MEDICATION_LOG" }

        val medicationEscalations = recent.count { it.type == "MEDICATION_ALERT" }

        val emergencyEvents = recent.count { it.type == "EMERGENCY_EVENT" }

        val routineLogs = recent.count { it.type == "ROUTINE_LOG" }

        return listOf(

            "Last 7 days: medication check-ins $medicationTaken.",

            "Last 7 days: medication escalations $medicationEscalations.",

            "Last 7 days: emergency assist events $emergencyEvents.",

            "Last 7 days: routine completions $routineLogs."

        )

    }

}


