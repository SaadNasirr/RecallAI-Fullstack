package com.example.recallai.data

import com.example.recallai.data.remote.ApiClient
import com.example.recallai.data.remote.CareAlertDispatchBody
import com.example.recallai.data.remote.CareAlertDto
import com.example.recallai.data.remote.CareEmergencyResponse
import com.example.recallai.data.remote.CareEmergencyTriggerBody
import com.example.recallai.data.remote.CareInviteGenerateBody
import com.example.recallai.data.remote.CareInviteRequestBody
import com.example.recallai.data.remote.CareOkResponse
import com.example.recallai.data.remote.CarePatientLocationBody
import com.example.recallai.data.remote.CarePermissionsDto
import com.example.recallai.data.remote.CareRelationshipDto
import com.example.recallai.data.remote.CareRelationshipEnvelope
import com.example.recallai.data.remote.CareTaskDto
import com.example.recallai.data.remote.CareWatchlistRowDto
import com.example.recallai.data.remote.CreateCareTaskRequest
import com.example.recallai.data.remote.DeviceTokenBody
import com.example.recallai.data.remote.SharePatientAddressBody
import com.example.recallai.data.remote.UnreadCountResponse
import com.example.recallai.data.remote.resolveUser
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

@Singleton
class CareRepository @Inject constructor() {

    private val api get() = ApiClient.api

    private val _selectedPatientId = MutableStateFlow<String?>(null)
    val selectedPatientId: StateFlow<String?> = _selectedPatientId.asStateFlow()

    private val _careConnectionBanner = MutableStateFlow<String?>(null)
    val careConnectionBanner: StateFlow<String?> = _careConnectionBanner.asStateFlow()

    fun showCareConnectionBanner(message: String) {
        _careConnectionBanner.value = message
    }

    fun clearCareConnectionBanner() {
        _careConnectionBanner.value = null
    }

    fun selectPatient(patientId: String?) {
        _selectedPatientId.value = patientId
    }

    /** Pick the first approved linked patient when none is selected (caregiver dashboard / memory sync). */
    suspend fun ensureDefaultPatientSelected(): String? = withContext(Dispatchers.IO) {
        if (!AuthManager.userRole.equals("caregiver", ignoreCase = true)) return@withContext null
        _selectedPatientId.value?.trim()?.takeIf { it.isNotBlank() }?.let { return@withContext it }
        val fromWatch = runCatching { watchlist() }.getOrDefault(emptyList())
            .firstNotNullOfOrNull { row ->
                row.relationship?.patientId.resolveUser()?._id?.trim()?.takeIf { it.isNotBlank() }
            }
        if (!fromWatch.isNullOrBlank()) {
            _selectedPatientId.value = fromWatch
            return@withContext fromWatch
        }
        val fromList = runCatching { myPatients() }.getOrDefault(emptyList())
            .firstNotNullOfOrNull { rel ->
                if (!rel.status.equals("approved", ignoreCase = true)) return@firstNotNullOfOrNull null
                rel.patientId.resolveUser()?._id?.trim()?.takeIf { it.isNotBlank() }
            }
        if (!fromList.isNullOrBlank()) {
            _selectedPatientId.value = fromList
        }
        fromList
    }

    suspend fun createQrInvite() = withContext(Dispatchers.IO) {
        api.careQrCreate()
    }

    suspend fun scanQrToken(token: String) = withContext(Dispatchers.IO) {
        api.careQrScan(CareInviteRequestBody(token = token.trim()))
    }

    suspend fun generateCodeInvite() = withContext(Dispatchers.IO) {
        api.careInviteGenerate(CareInviteGenerateBody(method = "code"))
    }

    suspend fun requestWithCode(code: String) = withContext(Dispatchers.IO) {
        api.careInviteRequest(CareInviteRequestBody(code = code.trim()))
    }

    suspend fun removeRelationship(relationshipId: String) = withContext(Dispatchers.IO) {
        val resp = api.careDeleteRelationship(relationshipId)
        if (!resp.isSuccessful) {
            val raw = resp.errorBody()?.use { it.string() }.orEmpty()
            val msg = try {
                org.json.JSONObject(raw).optString("message").takeIf { it.isNotBlank() }
            } catch (_: Exception) {
                null
            } ?: raw.ifBlank { "Could not remove link (${resp.code()})" }
            throw IllegalStateException(msg)
        }
    }

    suspend fun pendingForPatient(): List<CareRelationshipDto> = withContext(Dispatchers.IO) {
        api.carePending()
    }

    suspend fun approve(relationshipId: String): CareRelationshipEnvelope = withContext(Dispatchers.IO) {
        api.careApprove(relationshipId)
    }

    suspend fun reject(relationshipId: String) = withContext(Dispatchers.IO) {
        api.careReject(relationshipId)
    }

    suspend fun myCaregivers(): List<CareRelationshipDto> = withContext(Dispatchers.IO) {
        api.careMyCaregivers()
    }

    suspend fun myPatients(): List<CareRelationshipDto> = withContext(Dispatchers.IO) {
        api.careMyPatients()
    }

    suspend fun patchPermissions(relationshipId: String, patch: CarePermissionsDto) =
        withContext(Dispatchers.IO) {
            api.carePatchPermissions(relationshipId, patch)
        }

    suspend fun triggerEmergency(
        message: String? = null,
        type: String? = "sos",
        lat: Double? = null,
        lng: Double? = null
    ): CareEmergencyResponse =
        withContext(Dispatchers.IO) {
            api.careEmergencyTrigger(
                CareEmergencyTriggerBody(
                    message = message?.trim()?.takeIf { it.isNotBlank() },
                    metadata = null,
                    type = type,
                    lat = lat,
                    lng = lng
                )
            )
        }

    suspend fun alertsInbox(): List<CareAlertDto> = withContext(Dispatchers.IO) {
        api.careAlertsInbox()
    }

    suspend fun alertsUnreadCount(): Int = withContext(Dispatchers.IO) {
        api.careAlertsUnreadCount().count
    }

    suspend fun markAlertRead(alertId: String): CareOkResponse = withContext(Dispatchers.IO) {
        api.careAlertMarkRead(alertId)
    }

    suspend fun markAllAlertsRead(): CareOkResponse = withContext(Dispatchers.IO) {
        api.careAlertsMarkAllRead()
    }

    suspend fun careTasksForPatient(): List<CareTaskDto> = withContext(Dispatchers.IO) {
        api.careTasksList(null)
    }

    suspend fun careTasksForCaregiver(patientId: String): List<CareTaskDto> = withContext(Dispatchers.IO) {
        api.careTasksList(patientId)
    }

    suspend fun createCareTask(
        patientId: String,
        title: String,
        description: String?,
        priority: String?,
        dueAtIso: String?
    ): CareTaskDto = withContext(Dispatchers.IO) {
        api.careTasksCreate(
            CreateCareTaskRequest(
                patientId = patientId,
                title = title,
                description = description,
                priority = priority,
                dueAt = dueAtIso
            )
        )
    }

    suspend fun markCareTaskDone(taskId: String): CareTaskDto = withContext(Dispatchers.IO) {
        api.careTaskMarkDone(taskId)
    }

    suspend fun sharePatientAddress(address: String, lat: Double?, lng: Double?): CareEmergencyResponse =
        withContext(Dispatchers.IO) {
            api.carePatientShareAddress(SharePatientAddressBody(address, lat, lng))
        }

    suspend fun watchlist(): List<CareWatchlistRowDto> = withContext(Dispatchers.IO) {
        api.careWatchlist()
    }

    suspend fun registerDeviceToken(token: String): CareOkResponse = withContext(Dispatchers.IO) {
        api.postAuthDeviceToken(DeviceTokenBody(token))
    }

    suspend fun dispatchAlert(
        type: String,
        title: String,
        body: String,
        metadata: Map<String, Any?>? = null
    ) = withContext(Dispatchers.IO) {
        api.careAlertDispatch(CareAlertDispatchBody(type, title, body, metadata))
    }

    suspend fun pushPatientLiveLocation(lat: Double, lng: Double): CareOkResponse =
        withContext(Dispatchers.IO) {
            api.carePatientLocation(CarePatientLocationBody(lat, lng))
        }
}
