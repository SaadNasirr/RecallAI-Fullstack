package com.example.recallai.demo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.recallai.data.MemoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DemoModeUiState(
    val info: String = "Use quick simulators for FYP demo."
)

@HiltViewModel
class DemoModeViewModel @Inject constructor(
    private val memoryRepository: MemoryRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(DemoModeUiState())
    val uiState: StateFlow<DemoModeUiState> = _uiState.asStateFlow()

    fun simulateGeofenceAlert() = save("Simulated geofence breach event.", "Demo Geofence Alert", "GEOFENCE_ALERT")
    fun simulateMedicationMiss() = save("Simulated medication missed at 5 PM.", "Demo Medication Miss", "MEDICATION_ALERT")
    fun simulateFaceInsight() = save("Simulated face insight: anxious expression detected.", "Demo Face Insight", "FACE_ANALYSIS")

    private fun save(text: String, title: String, type: String) {
        viewModelScope.launch {
            memoryRepository.saveTextMemory(
                text = text,
                title = title,
                type = type,
                mood = "demo",
                tags = listOf("demo", type.lowercase())
            )
            _uiState.value = DemoModeUiState(info = "$title saved to timeline.")
        }
    }
}

