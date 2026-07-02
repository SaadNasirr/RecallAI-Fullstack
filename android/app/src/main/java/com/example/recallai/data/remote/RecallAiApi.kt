package com.example.recallai.data.remote

import okhttp3.MultipartBody
import okhttp3.ResponseBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.Response

// Add these data classes (put them near the top of the file, after SttResponse/TtsRequest)

data class LoginRequest(
    val email: String,
    val password: String,
    val role: String
)

data class RegisterRequest(
    val name: String,
    val email: String,
    val password: String,
    val phone: String,
    val age: Int? = null,
    val dateOfBirth: String? = null,
    val city: String? = null,
    val location: String? = null,
    val gender: String? = null,
    val role: String,
    val liveLat: Double? = null,
    val liveLng: Double? = null
)

data class AuthResponse(
    val message: String,
    val user: UserDto? = null
)

data class LoginResponse(
    val token: String,
    val user: UserDto? = null
)

/** GET /auth/me */
data class MeResponse(
    val user: UserDto? = null
)

data class UserDto(
    val _id: String,
    val name: String? = null,
    val email: String? = null,
    val gender: String? = null,
    val role: String? = null,
    val phone: String? = null,
    val liveLat: Double? = null,
    val liveLng: Double? = null,
    /** ISO timestamp from server */
    val liveLocationUpdatedAt: String? = null,
    val lastActiveAt: String? = null,
    val profileImageUrl: String? = null
)
data class SttResponse(
    val text: String
)

data class TtsRequest(
    val text: String
)

data class RecallChatRequest(
    val query: String,
    val patientId: String? = null
)

data class RecallChatResponse(
    val response: String,
    val sources: List<String>
)

data class AddMemoryRequest(
    val text: String,
    val patientId: String? = null
)

// Response shape varies slightly by backend implementation; we only need a minimal subset.
data class AddMemoryResponse(
    val _id: String? = null,
    val text: String? = null,
    val rawText: String? = null,
    val type: String? = null,
    val tags: String? = null
)

data class RemoteMemoryDto(
    val _id: String? = null,
    val text: String? = null,
    val rawText: String? = null,
    val type: String? = null,
    val tags: List<String>? = null,
    val createdAt: String? = null,
    val eventTime: String? = null
)

data class KnownPersonDto(
    val clientId: String,
    val name: String,
    val relation: String = "",
    val note: String = "",
    val phone: String = "",
    val updatedAt: Long = 0L
)

data class PeopleDirectoryResponse(
    val people: List<KnownPersonDto> = emptyList()
)

data class PeopleDirectoryPutRequest(
    val people: List<KnownPersonDto>,
    val patientId: String? = null
)

data class FaceProfileDto(
    val clientId: String,
    val name: String,
    val embedding: List<Float> = emptyList(),
    val updatedAt: Long = 0L
)

data class FaceProfilesResponse(
    val descriptorSchemaVersion: Int = 4,
    val profiles: List<FaceProfileDto> = emptyList()
)

data class FaceProfilesPutRequest(
    val profiles: List<FaceProfileDto>,
    val descriptorSchemaVersion: Int = 4,
    val patientId: String? = null
)

data class MedicationDto(
    val clientId: String,
    val name: String,
    val timeLabel: String = "",
    val notes: String = "",
    val takenToday: Boolean = false,
    val takenAt: Long? = null,
    val snoozeCount: Int = 0,
    val skippedToday: Boolean = false,
    val skipReason: String = "",
    val adherenceStatus: String = "PENDING",
    val lastResetDate: String = "",
    val updatedAt: Long = 0L
)

data class RoutineDto(
    val clientId: String,
    val title: String,
    val period: String = "Morning",
    val frequency: String = "Daily",
    val timeLabel: String = "",
    val doneToday: Boolean = false,
    val streakDays: Int = 0,
    val lastCompletedDate: String = "",
    val updatedAt: Long = 0L
)

data class ConsentDto(
    val shareWithCaregiver: Boolean = true,
    val allowLocationSharing: Boolean = true,
    val allowVoiceStorage: Boolean = true,
    val allowPhotoStorage: Boolean = true,
    val updatedAt: Long = 0L
)

data class ReminderDto(
    val clientId: String,
    val title: String,
    val description: String = "",
    val datetime: Long,
    val status: String = "PENDING",
    val source: String = "patient",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val warn10Min: Boolean = true,
    val preset: String = "",
    val repeatMode: String = "NONE",
    val daysOfWeekMask: Int = 0
)

data class AlarmDto(
    val clientId: String,
    val label: String,
    val hour: Int,
    val minute: Int,
    val repeatMode: String = "ONCE",
    val daysOfWeekMask: Int = 0,
    val enabled: Boolean = true,
    val nextTriggerAt: Long,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

data class PatientToolkitResponse(
    val medications: List<MedicationDto> = emptyList(),
    val routines: List<RoutineDto> = emptyList(),
    val consent: ConsentDto = ConsentDto(),
    val reminders: List<ReminderDto> = emptyList(),
    val alarms: List<AlarmDto> = emptyList()
)

data class PatientToolkitPutRequest(
    val medications: List<MedicationDto>,
    val routines: List<RoutineDto>,
    val consent: ConsentDto,
    val reminders: List<ReminderDto> = emptyList(),
    val alarms: List<AlarmDto> = emptyList(),
    val patientId: String? = null
)

data class LocatorResponse(
    val text_query: String,
    val response: String,
    val audio_url: String? = null
)

data class FaceAnalysisResponse(
    val faceCount: Int = 0,
    val dominantMood: String = "unknown",
    val confidence: Double = 0.0,
    val observations: List<String> = emptyList(),
    val careSuggestion: String = ""
)

data class FaceRecognizeResponse(
    val label: String = "Unknown",
    val score: Double = 0.0,
    val profilesCount: Int = 0
)

data class FaceEnrollResponse(
    val ok: Boolean = false,
    val name: String = "",
    val profilesCount: Int = 0
)

data class ActivityLogRequest(
    val type: String,
    val name: String,
    val description: String,
    val duration: Int = 0,
    val difficulty: String = "MEDIUM",
    val feedback: String? = null,
    val patientId: String? = null
)

data class MoodLogRequest(
    val score: Int,
    val note: String? = null,
    val context: String? = null,
    val activities: List<String> = emptyList(),
    val patientId: String? = null
)

data class CareQrCreateResponse(
    val token: String,
    val expiresAt: String
)

data class CareInviteGenerateBody(
    val method: String
)

data class CareInviteGenerateResponse(
    val token: String? = null,
    val shortCode: String? = null,
    val expiresAt: String
)

data class CareInviteRequestBody(
    val token: String? = null,
    val code: String? = null
)

data class CarePermissionsDto(
    val viewMemories: Boolean = false,
    val manageReminders: Boolean = false,
    val receiveAlerts: Boolean = false,
    val viewLocation: Boolean = false,
    val emergencyAccess: Boolean = false
)

data class CareRelationshipDto(
    val _id: String,
    val status: String? = null,
    val permissions: CarePermissionsDto? = null,
    val caregiverId: EmbeddedUserRef? = null,
    val patientId: EmbeddedUserRef? = null,
    val inviteMethod: String? = null,
    val relationshipType: String? = null
)

data class CareRelationshipEnvelope(
    val relationship: CareRelationshipDto
)

data class CarePatientLocationBody(
    val lat: Double,
    val lng: Double
)

data class CareOkResponse(
    val ok: Boolean? = null
)

data class CareEmergencyTriggerBody(
    val message: String? = null,
    val metadata: Map<String, String>? = null,
    val type: String? = null,
    val lat: Double? = null,
    val lng: Double? = null
)

data class CareEmergencyResponse(
    val alertId: String,
    val caregiverIds: List<String>
)

data class CareAlertDispatchBody(
    val type: String,
    val title: String,
    val body: String,
    val metadata: Map<String, Any?>? = null
)

data class CareAlertDto(
    val _id: String? = null,
    val patientId: Any? = null,
    val type: String? = null,
    val title: String? = null,
    val body: String? = null,
    val createdAt: String? = null,
    val unread: Boolean? = null,
    val readBy: List<Any>? = null
)

data class CareTaskDto(
    val _id: String,
    val patientId: String? = null,
    val caregiverId: String? = null,
    val caregiverName: String? = null,
    val title: String = "",
    val description: String? = null,
    val priority: String? = "MEDIUM",
    val dueAt: String? = null,
    val status: String? = null,
    val doneAt: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

data class CreateCareTaskRequest(
    val patientId: String,
    val title: String,
    val description: String? = null,
    val priority: String? = null,
    val dueAt: String? = null
)

data class SharePatientAddressBody(
    val address: String,
    val lat: Double? = null,
    val lng: Double? = null
)

data class UnreadCountResponse(val count: Int = 0)

data class DeviceTokenBody(val token: String)

data class CareWatchlistRowDto(
    val relationship: CareRelationshipDto? = null,
    val pendingTasks: Int = 0,
    val doneToday: Int = 0,
    val totalToday: Int = 0,
    val unreadEmergencies: Int = 0
)

data class CreateGeofenceRequest(
    val patientId: String,
    val name: String,
    val centerLat: Double,
    val centerLng: Double,
    val radiusMeters: Double,
    val color: String
)

data class GeofenceResponse(
    val _id: String,
    val patientId: String,
    val caregiverId: String,
    val name: String,
    val centerLat: Double,
    val centerLng: Double,
    val radiusMeters: Double,
    val color: String,
    val isActive: Boolean,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

data class GeofenceEventRequest(
    val geofenceId: String,
    val eventType: String,
    val lat: Double,
    val lng: Double
)

data class GeofenceEventResponse(
    val _id: String,
    val geofenceId: String,
    val zoneName: String,
    val patientId: String,
    val eventType: String,
    val triggeredAt: String,
    val location: GeofenceEventLocationDto
)

data class GeofenceEventLocationDto(
    val lat: Double,
    val lng: Double
)

interface RecallAiApi {

    @POST("/auth/login")
    suspend fun login(@Body body: LoginRequest): LoginResponse

    @GET("/auth/me")
    suspend fun getMe(): MeResponse

    @POST("/auth/register")
    suspend fun register(
        @Body body: RegisterRequest
    ): AuthResponse

    @POST("/auth/device-token")
    suspend fun postAuthDeviceToken(@Body body: DeviceTokenBody): CareOkResponse

    // VOICE

    @Multipart
    @POST("/api/voice/stt")
    suspend fun speechToText(
        @Part audio: MultipartBody.Part
    ): SttResponse

    @POST("/api/voice/tts")
    suspend fun textToSpeech(
        @Body body: TtsRequest
    ): ResponseBody

    // CHAT

    @GET("/chat/sessions")
    suspend fun getChatSessions(@Query("patientId") patientId: String? = null): List<ChatSession>

    @POST("/chat/sessions")
    suspend fun createChatSession(@Query("patientId") patientId: String? = null): CreateSessionResponse

    @POST("/chat/sessions/{sessionId}/messages")
    suspend fun sendChatMessage(
        @Path("sessionId") sessionId: String,
        @Body body: ChatMessageRequest,
        @Query("patientId") patientId: String? = null
    ): ChatMessageResponse

    // RECALL ASSISTANT

    @POST("/recall/chat")
    suspend fun recallChat(@Body body: RecallChatRequest): RecallChatResponse

    // REMOTE MEMORY BANK (used so backend recall can find the same memories the UI saves)

    @POST("/memory")
    suspend fun addRemoteMemory(@Body body: AddMemoryRequest): AddMemoryResponse

    @GET("/memory")
    suspend fun getRemoteMemories(@Query("patientId") patientId: String? = null): List<RemoteMemoryDto>

    // PEOPLE DIRECTORY (People Book cloud sync)

    @GET("/people-directory")
    suspend fun getPeopleDirectory(@Query("patientId") patientId: String? = null): PeopleDirectoryResponse

    @PUT("/people-directory")
    suspend fun putPeopleDirectory(@Body body: PeopleDirectoryPutRequest): PeopleDirectoryResponse

    // FACE PROFILES (Face Insights template cloud sync)

    @GET("/face-profiles")
    suspend fun getFaceProfiles(@Query("patientId") patientId: String? = null): FaceProfilesResponse

    @PUT("/face-profiles")
    suspend fun putFaceProfiles(@Body body: FaceProfilesPutRequest): FaceProfilesResponse

    // PATIENT TOOLKIT (medications, routines, consent cloud sync)

    @GET("/patient-toolkit")
    suspend fun getPatientToolkit(@Query("patientId") patientId: String? = null): PatientToolkitResponse

    @PUT("/patient-toolkit")
    suspend fun putPatientToolkit(@Body body: PatientToolkitPutRequest): PatientToolkitResponse

    // VISION / OBJECT DETECTION
    // Backend contract: multipart/form-data with fields:
    // - image (required)
    // - audio (optional; handled via separate method here)
    // - query (optional text field in req.body.query)

    @Multipart
    @POST("/api/locator")
    suspend fun analyzeObjectLocator(
        @Part image: MultipartBody.Part,
        @Part("query") query: RequestBody,
        @Part("patientId") patientId: RequestBody? = null
    ): LocatorResponse

    @Multipart
    @POST("/api/locator")
    suspend fun analyzeObjectLocatorWithAudio(
        @Part image: MultipartBody.Part,
        @Part audio: MultipartBody.Part,
        @Part("query") query: RequestBody,
        @Part("patientId") patientId: RequestBody? = null
    ): LocatorResponse

    @Multipart
    @POST("/api/face")
    suspend fun analyzeFace(
        @Part image: MultipartBody.Part,
        @Part("contextHint") contextHint: RequestBody,
        @Part("patientId") patientId: RequestBody? = null
    ): FaceAnalysisResponse

    @Multipart
    @POST("/api/face/recognize")
    suspend fun recognizeFace(
        @Part image: MultipartBody.Part,
        @Part("patientId") patientId: RequestBody? = null
    ): FaceRecognizeResponse

    @Multipart
    @POST("/api/face/enroll")
    suspend fun enrollFace(
        @Part image: MultipartBody.Part,
        @Part("name") name: RequestBody,
        @Part("patientId") patientId: RequestBody? = null
    ): FaceEnrollResponse

    @POST("/api/activity")
    suspend fun logActivity(@Body body: ActivityLogRequest): Any

    @POST("/api/mood")
    suspend fun logMood(@Body body: MoodLogRequest): Any

    @POST("/care/qr/create")
    suspend fun careQrCreate(): CareQrCreateResponse

    @POST("/care/qr/scan")
    suspend fun careQrScan(@Body body: CareInviteRequestBody): CareRelationshipEnvelope

    @POST("/care/invite/generate")
    suspend fun careInviteGenerate(@Body body: CareInviteGenerateBody): CareInviteGenerateResponse

    @POST("/care/invite/request")
    suspend fun careInviteRequest(@Body body: CareInviteRequestBody): CareRelationshipEnvelope

    @DELETE("/care/relationship/{id}")
    suspend fun careDeleteRelationship(@Path("id") id: String): Response<Unit>

    @POST("/care/request/{id}/approve")
    suspend fun careApprove(@Path("id") id: String): CareRelationshipEnvelope

    @POST("/care/request/{id}/reject")
    suspend fun careReject(@Path("id") id: String): CareRelationshipEnvelope

    @GET("/care/my-caregivers")
    suspend fun careMyCaregivers(): List<CareRelationshipDto>

    @GET("/care/my-patients")
    suspend fun careMyPatients(): List<CareRelationshipDto>

    @GET("/care/pending")
    suspend fun carePending(): List<CareRelationshipDto>

    @PATCH("/care/permissions/{id}")
    suspend fun carePatchPermissions(
        @Path("id") id: String,
        @Body body: CarePermissionsDto
    ): CareRelationshipEnvelope

    @POST("/care/emergency/trigger")
    suspend fun careEmergencyTrigger(@Body body: CareEmergencyTriggerBody): CareEmergencyResponse

    @POST("/care/patient/location")
    suspend fun carePatientLocation(@Body body: CarePatientLocationBody): CareOkResponse

    @POST("/care/alerts/dispatch")
    suspend fun careAlertDispatch(@Body body: CareAlertDispatchBody): CareEmergencyResponse

    @GET("/care/alerts/inbox")
    suspend fun careAlertsInbox(): List<CareAlertDto>

    @GET("/care/alerts/unread-count")
    suspend fun careAlertsUnreadCount(): UnreadCountResponse

    @PATCH("/care/alerts/{id}/read")
    suspend fun careAlertMarkRead(@Path("id") id: String): CareOkResponse

    @POST("/care/alerts/mark-all-read")
    suspend fun careAlertsMarkAllRead(): CareOkResponse

    @GET("/care/tasks")
    suspend fun careTasksList(@Query("patientId") patientId: String? = null): List<CareTaskDto>

    @POST("/care/tasks")
    suspend fun careTasksCreate(@Body body: CreateCareTaskRequest): CareTaskDto

    @PATCH("/care/tasks/{id}/done")
    suspend fun careTaskMarkDone(@Path("id") id: String): CareTaskDto

    @POST("/care/patient/share-address")
    suspend fun carePatientShareAddress(@Body body: SharePatientAddressBody): CareEmergencyResponse

    @GET("/care/watchlist")
    suspend fun careWatchlist(): List<CareWatchlistRowDto>

    // GEOFENCE
    @POST("/geofence/create")
    suspend fun createGeofence(@Body body: CreateGeofenceRequest): Response<GeofenceResponse>

    @GET("/geofence/my-zones")
    suspend fun getMyGeofenceZones(@Query("patientId") patientId: String? = null): List<GeofenceResponse>

    @DELETE("/geofence/{id}")
    suspend fun deleteGeofence(@Path("id") id: String): Response<Unit>

    @PATCH("/geofence/{id}/toggle")
    suspend fun toggleGeofence(@Path("id") id: String): Response<GeofenceResponse>

    @POST("/geofence/event")
    suspend fun reportGeofenceEvent(@Body body: GeofenceEventRequest): Response<Unit>

    @GET("/geofence/events/{patientId}")
    suspend fun getGeofenceEvents(@Path("patientId") patientId: String): List<GeofenceEventResponse>
}