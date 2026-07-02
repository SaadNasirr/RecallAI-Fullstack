package com.example.recallai.geofence

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.recallai.data.GeofenceRepository
import com.example.recallai.data.remote.GeofenceEventResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GeofenceAlertHistoryUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val events: List<GeofenceEventResponse> = emptyList()
)

@HiltViewModel
class GeofenceAlertHistoryViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val geofenceRepository: GeofenceRepository
) : ViewModel() {

    val patientId: String = savedStateHandle.get<String>("patientId") ?: ""

    private val _state = MutableStateFlow(GeofenceAlertHistoryUiState())
    val uiState = _state.asStateFlow()

    fun load() {
        if (patientId.isBlank()) return
        viewModelScope.launch {
            _state.value = GeofenceAlertHistoryUiState(loading = true)
            geofenceRepository.eventsForPatient(patientId).fold(
                onSuccess = { list ->
                    _state.value = GeofenceAlertHistoryUiState(events = list)
                },
                onFailure = { e ->
                    _state.value = GeofenceAlertHistoryUiState(
                        error = e.message ?: "Could not load"
                    )
                }
            )
        }
    }
}
