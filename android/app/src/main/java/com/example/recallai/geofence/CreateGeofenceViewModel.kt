package com.example.recallai.geofence

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.recallai.data.CareRepository
import com.example.recallai.data.GeofenceRepository
import com.example.recallai.data.remote.CreateGeofenceRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CreateGeofenceUiState(
    val saving: Boolean = false,
    val error: String? = null,
    val done: Boolean = false
)

@HiltViewModel
class CreateGeofenceViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val geofenceRepository: GeofenceRepository,
    private val careRepository: CareRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CreateGeofenceUiState())
    val uiState = _state.asStateFlow()

    /** Nav arg `patientId`, or caregiver’s currently selected patient from the shell. */
    private fun resolvedPatientId(): String {
        val fromNav = savedStateHandle.get<String>("patientId")?.trim().orEmpty()
        if (fromNav.isNotBlank()) return fromNav
        return careRepository.selectedPatientId.value?.trim().orEmpty()
    }

    fun saveZone(
        name: String,
        centerLat: Double,
        centerLng: Double,
        radiusMeters: Double,
        colorHex: String
    ) {
        val patientId = resolvedPatientId()
        if (patientId.isBlank()) {
            _state.value = CreateGeofenceUiState(
                saving = false,
                error = "No patient selected. Open Zones, pick a patient, then Add New Zone."
            )
            return
        }
        viewModelScope.launch {
            _state.value = CreateGeofenceUiState(saving = true)
            val body = CreateGeofenceRequest(
                patientId = patientId,
                name = name.trim(),
                centerLat = centerLat,
                centerLng = centerLng,
                radiusMeters = radiusMeters,
                color = colorHex
            )
            geofenceRepository.createZone(body).fold(
                onSuccess = {
                    _state.value = CreateGeofenceUiState(done = true)
                },
                onFailure = { e ->
                    _state.value = CreateGeofenceUiState(
                        saving = false,
                        error = e.message ?: "Save failed"
                    )
                }
            )
        }
    }

    fun clearDone() {
        _state.value = _state.value.copy(done = false)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
