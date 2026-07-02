package com.example.recallai.medication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.recallai.data.CareToolkitRepository
import com.example.recallai.data.MedicationItem
import com.example.recallai.data.MemoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

data class MedicationUiState(
    val medications: List<MedicationItem> = emptyList(),
    val nameInput: String = "",
    val timeInput: String = "",
    val noteInput: String = "",
    val skipReasonInput: String = "",
    val escalationsToday: Int = 0,
    val info: String? = null
) {
    val takenCount: Int get() = medications.count { it.takenToday }
    val pendingCount: Int get() = medications.count { !it.takenToday && !it.skippedToday }
    val missedCount: Int get() = medications.count { it.adherenceStatus == "MISSED" }
    val adherencePct: Int get() = if (medications.isEmpty()) 100
        else (takenCount * 100 / medications.size)
}

@HiltViewModel
class MedicationSchedulerViewModel @Inject constructor(
    private val toolkitRepository: CareToolkitRepository,
    private val memoryRepository: MemoryRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(MedicationUiState(medications = toolkitRepository.getMedications()))
    val uiState: StateFlow<MedicationUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            toolkitRepository.syncPatientToolkitFromServer()
            refreshFromLocal()
        }
    }

    private fun refreshFromLocal() {
        _uiState.update { it.copy(medications = toolkitRepository.getMedications()) }
    }

    fun onNameChange(v: String) = _uiState.update { it.copy(nameInput = v, info = null) }
    fun onTimeChange(v: String) = _uiState.update { it.copy(timeInput = v, info = null) }
    fun onNoteChange(v: String) = _uiState.update { it.copy(noteInput = v, info = null) }
    fun onSkipReasonChange(v: String) = _uiState.update { it.copy(skipReasonInput = v, info = null) }

    fun addMedication(): MedicationItem? {
        val s = _uiState.value
        val name = s.nameInput.trim()
        val time = s.timeInput.trim()
        if (name.isEmpty() || time.isEmpty()) {
            _uiState.value = s.copy(info = "Enter medication name and time.")
            return null
        }
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val now = System.currentTimeMillis()
        val item = MedicationItem(
            id = UUID.randomUUID().toString(),
            name = name,
            timeLabel = time,
            notes = s.noteInput.trim(),
            adherenceStatus = "PENDING",
            lastResetDate = today,
            updatedAt = now
        )
        val updated = listOf(item) + s.medications
        viewModelScope.launch {
            toolkitRepository.saveMedications(updated)
            _uiState.value = s.copy(
                medications = updated,
                nameInput = "",
                timeInput = "",
                noteInput = "",
                skipReasonInput = "",
                info = "Medication scheduled."
            )
            memoryRepository.saveTextMemory(
                text = "Medication scheduled: ${item.name} at ${item.timeLabel}. ${item.notes}",
                title = "Medication Reminder",
                type = "MEDICATION",
                tags = listOf("medication", "reminder")
            )
        }
        return item
    }

    fun markTaken(itemId: String) {
        val s = _uiState.value
        val now = System.currentTimeMillis()
        val updated = s.medications.map {
            if (it.id == itemId) {
                it.copy(
                    takenToday = true,
                    skippedToday = false,
                    skipReason = "",
                    takenAt = now,
                    adherenceStatus = "TAKEN",
                    updatedAt = now
                )
            } else it
        }
        viewModelScope.launch {
            toolkitRepository.saveMedications(updated)
            _uiState.value = s.copy(medications = updated, info = "Marked as taken.")
            val changed = updated.firstOrNull { it.id == itemId } ?: return@launch
            memoryRepository.saveTextMemory(
                text = "Medication ${changed.name} marked taken at ${changed.timeLabel}.",
                title = "Medication Check-in",
                type = "MEDICATION_LOG",
                tags = listOf("medication", "log")
            )
        }
    }

    fun snoozeDose(itemId: String) {
        val s = _uiState.value
        val now = System.currentTimeMillis()
        val updated = s.medications.map {
            if (it.id == itemId) {
                it.copy(
                    snoozeCount = it.snoozeCount + 1,
                    adherenceStatus = "SNOOZED",
                    updatedAt = now
                )
            } else it
        }
        viewModelScope.launch {
            toolkitRepository.saveMedications(updated)
            _uiState.value = s.copy(medications = updated, info = "Dose snoozed.")
        }
    }

    fun skipDose(itemId: String) {
        val s = _uiState.value
        val reason = s.skipReasonInput.trim().ifBlank { "No reason provided" }
        val now = System.currentTimeMillis()
        val updated = s.medications.map {
            if (it.id == itemId) {
                it.copy(
                    takenToday = false,
                    skippedToday = true,
                    skipReason = reason,
                    adherenceStatus = "SKIPPED",
                    updatedAt = now
                )
            } else it
        }
        viewModelScope.launch {
            toolkitRepository.saveMedications(updated)
            _uiState.value = s.copy(
                medications = updated,
                skipReasonInput = "",
                info = "Dose skipped and logged."
            )
            val changed = updated.firstOrNull { it.id == itemId } ?: return@launch
            memoryRepository.saveTextMemory(
                text = "Medication ${changed.name} skipped. Reason: ${changed.skipReason}.",
                title = "Medication Skip",
                type = "MEDICATION_LOG",
                tags = listOf("medication", "skip")
            )
        }
    }

    fun removeMedication(id: String) {
        val updated = _uiState.value.medications.filterNot { it.id == id }
        viewModelScope.launch {
            toolkitRepository.saveMedications(updated)
            _uiState.update { it.copy(medications = updated, info = "Medication removed.") }
        }
    }

    fun runAdherenceCheck() {
        val s = _uiState.value
        val missed = s.medications.filter { !it.takenToday && !it.skippedToday }
        if (missed.isEmpty()) {
            _uiState.update { it.copy(escalationsToday = 0, info = "All doses are handled for today.") }
            return
        }
        val now = System.currentTimeMillis()
        val updated = s.medications.map {
            if (!it.takenToday && !it.skippedToday) {
                it.copy(adherenceStatus = "MISSED", updatedAt = now)
            } else it
        }
        viewModelScope.launch {
            toolkitRepository.saveMedications(updated)
            _uiState.update {
                it.copy(
                    medications = updated,
                    escalationsToday = missed.size,
                    info = "${missed.size} missed dose(s) flagged for caregiver follow-up."
                )
            }
            missed.forEach { med ->
                memoryRepository.saveTextMemory(
                    text = "Medication adherence alert: ${med.name} at ${med.timeLabel} appears missed.",
                    title = "Medication Escalation",
                    type = "MEDICATION_ALERT",
                    tags = listOf("medication", "alert", "caregiver")
                )
            }
        }
    }
}
