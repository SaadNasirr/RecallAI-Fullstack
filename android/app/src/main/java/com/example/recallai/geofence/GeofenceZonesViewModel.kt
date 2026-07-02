package com.example.recallai.geofence

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.recallai.data.CareRepository
import com.example.recallai.data.GeofenceRepository
import com.example.recallai.data.remote.GeofenceResponse
import com.example.recallai.data.remote.resolveUser
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GeofenceZonesUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val patientOptions: List<Pair<String, String>> = emptyList(),
    val selectedPatientId: String? = null,
    val selectedPatientName: String = "",
    val zones: List<GeofenceResponse> = emptyList(),
    /** False when the caregiver has no approved patient links (cannot add zones). */
    val hasLinkedPatients: Boolean = true
)

@HiltViewModel
class GeofenceZonesViewModel @Inject constructor(
    private val geofenceRepository: GeofenceRepository,
    private val careRepository: CareRepository
) : ViewModel() {

    private val _state = MutableStateFlow(GeofenceZonesUiState())
    val uiState = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val patientsResult = runCatching { careRepository.myPatients() }
            patientsResult.fold(
                onSuccess = { links ->
                    val opts = links.mapNotNull { rel ->
                        val p = rel.patientId.resolveUser() ?: return@mapNotNull null
                        val id = p._id
                        val name = p.name?.takeIf { it.isNotBlank() } ?: "Patient"
                        id to name
                    }
                    val rawSel = _state.value.selectedPatientId
                        ?: careRepository.selectedPatientId.value
                        ?: opts.firstOrNull()?.first
                    val sel = rawSel?.takeIf { id -> opts.any { it.first == id } }
                        ?: opts.firstOrNull()?.first
                    _state.value = _state.value.copy(
                        patientOptions = opts,
                        selectedPatientId = sel,
                        selectedPatientName = opts.firstOrNull { it.first == sel }?.second ?: "",
                        hasLinkedPatients = opts.isNotEmpty(),
                        loading = false
                    )
                    sel?.let { refreshZones(it) }
                    if (sel == null) {
                        _state.value = _state.value.copy(loading = false, zones = emptyList())
                    }
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(
                        loading = false,
                        error = e.message ?: "Could not load patients"
                    )
                }
            )
        }
    }

    fun selectPatient(patientId: String) {
        viewModelScope.launch {
            careRepository.selectPatient(patientId)
            val name = _state.value.patientOptions.firstOrNull { it.first == patientId }?.second ?: ""
            _state.value = _state.value.copy(selectedPatientId = patientId, selectedPatientName = name)
            refreshZones(patientId)
        }
    }

    fun refreshZones(patientId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            geofenceRepository.listZones(patientId).fold(
                onSuccess = { list ->
                    _state.value = _state.value.copy(zones = list, loading = false)
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(
                        loading = false,
                        error = e.message ?: "Could not load zones"
                    )
                }
            )
        }
    }

    fun toggleZone(id: String) {
        viewModelScope.launch {
            geofenceRepository.toggleZone(id).fold(
                onSuccess = { updated ->
                    val list = _state.value.zones.map { z ->
                        if (z._id == updated._id) updated else z
                    }
                    _state.value = _state.value.copy(zones = list)
                },
                onFailure = { }
            )
        }
    }

    fun deleteZone(id: String) {
        viewModelScope.launch {
            geofenceRepository.deleteZone(id).fold(
                onSuccess = {
                    _state.value = _state.value.copy(
                        zones = _state.value.zones.filter { it._id != id }
                    )
                },
                onFailure = { }
            )
        }
    }
}
