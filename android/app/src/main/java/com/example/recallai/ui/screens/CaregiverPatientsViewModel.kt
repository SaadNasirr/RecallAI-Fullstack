package com.example.recallai.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.recallai.data.CareRepository
import com.example.recallai.data.MemoryRepository
import com.example.recallai.data.remote.CareWatchlistRowDto
import com.example.recallai.data.remote.UserDto
import com.example.recallai.data.remote.resolveUser
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class CaregiverPatientItem(
    val patientId: String,
    val name: String,
    val relation: String,
    val tasksTodayLabel: String,
    val unreadEmergency: Int,
    val lastActiveLabel: String,
    val locationShort: String,
    val isSelected: Boolean
)

data class CaregiverPatientsUiState(
    val isLoading: Boolean = true,
    val patients: List<CaregiverPatientItem> = emptyList()
)

@HiltViewModel
class CaregiverPatientsViewModel @Inject constructor(
    private val careRepository: CareRepository,
    private val memoryRepository: MemoryRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(CaregiverPatientsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            while (isActive) {
                delay(25_000)
                refresh()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val watchRows = runCatching { careRepository.watchlist() }.getOrElse { emptyList() }
            var selectedId = careRepository.selectedPatientId.value
            if (watchRows.size == 1 && selectedId.isNullOrBlank()) {
                val onlyPid = watchRows.firstOrNull()?.relationship?.patientId.resolveUser()?._id?.trim()
                if (!onlyPid.isNullOrBlank()) {
                    careRepository.selectPatient(onlyPid)
                    selectedId = onlyPid
                }
            }

            val patients = watchRows.mapNotNull { row -> mapRow(row, selectedId) }
                .sortedWith(
                    compareBy<CaregiverPatientItem> { if (it.isSelected) 0 else 1 }
                        .thenByDescending { it.unreadEmergency }
                        .thenBy { it.name.lowercase(Locale.getDefault()) }
                )

            _uiState.value = CaregiverPatientsUiState(isLoading = false, patients = patients)
        }
    }

    private fun mapRow(row: CareWatchlistRowDto, selectedId: String?): CaregiverPatientItem? {
        val rel = row.relationship ?: return null
        val patient = rel.patientId.resolveUser() ?: return null
        val pid = patient._id.trim().ifBlank { return null }
        val name = patient.name?.trim()?.takeIf { it.isNotEmpty() }
            ?: patient.email?.substringBefore("@")?.trim()?.takeIf { it.isNotEmpty() }
            ?: "Patient"
        val relation = rel.relationshipType?.replace('_', ' ')?.trim()?.takeIf { it.isNotEmpty() }
            ?: "Linked"
        val tasksTodayLabel = when {
            row.totalToday > 0 -> "${row.doneToday} of ${row.totalToday} tasks today"
            row.pendingTasks > 0 -> "${row.pendingTasks} open tasks"
            else -> "No tasks today"
        }
        return CaregiverPatientItem(
            patientId = pid,
            name = name,
            relation = relation,
            tasksTodayLabel = tasksTodayLabel,
            unreadEmergency = row.unreadEmergencies,
            lastActiveLabel = lastActiveLabel(patient),
            locationShort = locationShort(patient),
            isSelected = pid == selectedId
        )
    }

    private fun lastActiveLabel(user: UserDto): String {
        val iso = user.lastActiveAt?.trim().orEmpty()
        if (iso.isBlank()) return "Waiting for patient activity"
        val ms = runCatching { Instant.parse(iso).toEpochMilli() }.getOrNull() ?: return "Waiting for patient activity"
        val mins = (System.currentTimeMillis() - ms) / 60_000L
        return when {
            mins < 10L -> "Active ${mins.coerceAtLeast(1L)} min ago"
            mins < 60L -> "Active $mins min ago"
            mins < 1440L -> "Last seen ${mins / 60L} hours ago"
            else -> "Last seen ${mins / 1440L} days ago"
        }
    }

    private fun locationShort(user: UserDto): String {
        val lat = user.liveLat
        val lng = user.liveLng
        if (lat == null || lng == null) return "—"
        val updated = user.liveLocationUpdatedAt?.let { formatShortAge(it) }
        val suffix = updated?.let { " · $it" } ?: ""
        return "%.3f, %.3f".format(lat, lng) + suffix
    }

    private fun formatShortAge(iso: String): String? {
        val ms = runCatching { Instant.parse(iso).toEpochMilli() }.getOrNull() ?: return null
        val mins = (System.currentTimeMillis() - ms) / 60_000L
        return when {
            mins < 60L -> "${mins}m ago"
            mins < 1440L -> "${mins / 60L}h ago"
            else -> "${mins / 1440L}d ago"
        }
    }

    fun selectPatientForCaregiving(patientId: String) {
        careRepository.selectPatient(patientId)
        viewModelScope.launch { memoryRepository.syncFromServer() }
        refresh()
    }
}
