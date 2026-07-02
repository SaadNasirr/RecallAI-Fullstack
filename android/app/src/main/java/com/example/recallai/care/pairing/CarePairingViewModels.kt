package com.example.recallai.care.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.recallai.data.CareRepository
import com.example.recallai.data.remote.CarePermissionsDto
import com.example.recallai.data.remote.CareInviteGenerateResponse
import com.example.recallai.data.remote.CareQrCreateResponse
import com.example.recallai.data.remote.CareRelationshipDto
import com.example.recallai.data.remote.resolveUser
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PatientCarePairingUiState(
    val qrPayload: CareQrCreateResponse? = null,
    val backupInvite: CareInviteGenerateResponse? = null,
    val pending: List<CareRelationshipDto> = emptyList(),
    val linked: List<CareRelationshipDto> = emptyList(),
    val message: String? = null,
    val busy: Boolean = false
)

@HiltViewModel
class PatientCarePairingViewModel @Inject constructor(
    private val careRepository: CareRepository
) : ViewModel() {

    private val _state = MutableStateFlow(PatientCarePairingUiState())
    val state = _state.asStateFlow()

    val careConnectionBanner = careRepository.careConnectionBanner

    fun clearCareConnectionBanner() {
        careRepository.clearCareConnectionBanner()
    }

    fun refreshAll() {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, message = null)
            runCatching {
                val p = careRepository.pendingForPatient()
                val l = careRepository.myCaregivers()
                _state.value = _state.value.copy(pending = p, linked = l, busy = false)
            }.onFailure {
                _state.value = _state.value.copy(busy = false, message = it.message ?: "Could not load")
            }
        }
    }

    fun refreshQr() {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, message = null)
            runCatching {
                val qr = careRepository.createQrInvite()
                _state.value = _state.value.copy(qrPayload = qr, busy = false)
            }.onFailure {
                _state.value = _state.value.copy(busy = false, message = it.message ?: "Could not create QR")
            }
        }
    }

    fun refreshBackupCode() {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, message = null)
            runCatching {
                val b = careRepository.generateCodeInvite()
                _state.value = _state.value.copy(backupInvite = b, busy = false)
            }.onFailure {
                _state.value = _state.value.copy(busy = false, message = it.message ?: "Could not create code")
            }
        }
    }

    fun approve(id: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            runCatching {
                val env = careRepository.approve(id)
                val cg = env.relationship.caregiverId.resolveUser()?.name?.trim()?.takeIf { it.isNotBlank() }
                    ?: "your caregiver"
                careRepository.showCareConnectionBanner("You are now connected with $cg.")
                refreshAll()
                _state.value = _state.value.copy(busy = false, message = null)
                onDone()
            }.onFailure {
                _state.value = _state.value.copy(busy = false, message = it.message)
            }
        }
    }

    fun reject(id: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            runCatching {
                careRepository.reject(id)
                refreshAll()
            }.onFailure {
                _state.value = _state.value.copy(busy = false, message = it.message)
            }
        }
    }

    fun savePermissions(relationshipId: String, p: CarePermissionsDto, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, message = null)
            runCatching {
                careRepository.patchPermissions(relationshipId, p)
                refreshAll()
                _state.value = _state.value.copy(busy = false, message = "Saved.")
                onDone()
            }.onFailure {
                _state.value = _state.value.copy(busy = false, message = it.message)
            }
        }
    }

    fun removeLinkedCaregiver(relationshipId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, message = null)
            runCatching {
                careRepository.removeRelationship(relationshipId)
                val p = careRepository.pendingForPatient()
                val l = careRepository.myCaregivers()
                _state.value = _state.value.copy(
                    pending = p,
                    linked = l,
                    busy = false,
                    message = "Caregiver removed from your circle."
                )
            }.onFailure {
                _state.value = _state.value.copy(busy = false, message = it.message ?: "Could not remove")
            }
        }
    }
}

data class CaregiverCarePairingUiState(
    val patients: List<CareRelationshipDto> = emptyList(),
    val message: String? = null,
    val busy: Boolean = false
)

@HiltViewModel
class CaregiverCarePairingViewModel @Inject constructor(
    private val careRepository: CareRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CaregiverCarePairingUiState())
    val state = _state.asStateFlow()

    val careConnectionBanner = careRepository.careConnectionBanner
    val selectedPatientId = careRepository.selectedPatientId

    fun clearCareConnectionBanner() {
        careRepository.clearCareConnectionBanner()
    }

    fun refreshPatients() {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, message = null)
            runCatching {
                val list = careRepository.myPatients()
                _state.value = _state.value.copy(patients = list, busy = false)
            }.onFailure {
                _state.value = _state.value.copy(busy = false, message = it.message ?: "Could not load")
            }
        }
    }

    private var scanInFlight = false

    fun submitScannedToken(raw: String, onDone: () -> Unit) {
        CareQrCodec.logDecodedQr(raw)
        val token = CareQrCodec.decodeInviteToken(raw)
        if (token.isBlank()) {
            _state.value = _state.value.copy(message = "QR code did not contain a valid invite token.")
            return
        }
        if (scanInFlight || _state.value.busy) return
        scanInFlight = true
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, message = null)
            runCatching {
                val env = careRepository.scanQrToken(token)
                val list = careRepository.myPatients()
                val msg = when (env.relationship.status) {
                    "approved" -> {
                        val pn = env.relationship.patientId.resolveUser()?.name?.trim()?.takeIf { it.isNotBlank() }
                            ?: "your patient"
                        careRepository.showCareConnectionBanner("You are now connected with $pn.")
                        "You're already connected with this patient."
                    }
                    "pending" -> "Request sent. They approve the link on their phone."
                    else -> "Request sent."
                }
                _state.value = _state.value.copy(patients = list, busy = false, message = msg)
                onDone()
            }.onFailure {
                _state.value = _state.value.copy(busy = false, message = it.message ?: "Scan failed")
            }
            scanInFlight = false
        }
    }

    fun submitCode(code: String, onDone: () -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, message = null)
            runCatching {
                val env = careRepository.requestWithCode(code)
                val list = careRepository.myPatients()
                val msg = when (env.relationship.status) {
                    "approved" -> {
                        val pn = env.relationship.patientId.resolveUser()?.name?.trim()?.takeIf { it.isNotBlank() }
                            ?: "your patient"
                        careRepository.showCareConnectionBanner("You are now connected with $pn.")
                        "You're already connected with this patient."
                    }
                    "pending" -> "Request sent. They approve the link on their phone."
                    else -> "Request sent."
                }
                _state.value = _state.value.copy(patients = list, busy = false, message = msg)
                onDone()
            }.onFailure {
                _state.value = _state.value.copy(busy = false, message = it.message ?: "Could not send code")
            }
        }
    }

    fun removePatientRelationship(relationshipId: String) {
        viewModelScope.launch {
            val removedPid =
                _state.value.patients.find { it._id == relationshipId }?.patientId.resolveUser()?._id
            _state.value = _state.value.copy(busy = true, message = null)
            runCatching {
                careRepository.removeRelationship(relationshipId)
                if (!removedPid.isNullOrBlank() &&
                    careRepository.selectedPatientId.value == removedPid
                ) {
                    careRepository.selectPatient(null)
                }
                val list = careRepository.myPatients()
                _state.value = _state.value.copy(
                    patients = list,
                    busy = false,
                    message = "Patient removed from your list."
                )
            }.onFailure {
                _state.value = _state.value.copy(busy = false, message = it.message ?: "Could not remove")
            }
        }
    }

    fun selectPatientForCare(patientUserId: String) {
        careRepository.selectPatient(patientUserId)
    }
}
