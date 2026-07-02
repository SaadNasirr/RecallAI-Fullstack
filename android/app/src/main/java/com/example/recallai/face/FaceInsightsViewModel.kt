package com.example.recallai.face

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.recallai.data.CareToolkitRepository
import com.example.recallai.data.FaceRepository
import com.example.recallai.data.KnownPersonItem
import com.example.recallai.data.MemoryRepository
import com.example.recallai.data.local.MemoryEntity
import com.example.recallai.data.FaceProfileItem
import com.example.recallai.memories.MemoryOpenCoordinator
import com.example.recallai.memories.MemoryOpenPayload
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

enum class LiveFaceUiMode {
    Idle,
    CameraLoading,
    NoFace,
    Identifying,
    KnownHigh,
    KnownLow,
    UnknownFace
}

data class FaceInsightsUiState(
    val isAnalyzing: Boolean = false,
    val backendSummary: String? = null,
    val backendError: String? = null,
    val enrollmentError: String? = null,
    val enrollSuccess: String? = null,
    val knownPeople: List<KnownPersonItem> = emptyList(),
    val profilesCount: Int = 0,
    val savedFaces: List<FaceProfileItem> = emptyList(),
    val recentFaceMemories: List<MemoryEntity> = emptyList(),
    val liveMode: LiveFaceUiMode = LiveFaceUiMode.Idle,
    val liveLabel: String = "Unknown",
    val liveConfidenceWords: String = "Unknown",
    val moodHint: String = "No photo yet",
    val enrollmentSamplesCollected: Int = 0
)

@HiltViewModel
class FaceInsightsViewModel @Inject constructor(
    private val memoryRepository: MemoryRepository,
    private val faceRepository: FaceRepository,
    private val careToolkitRepository: CareToolkitRepository,
    private val memoryOpenCoordinator: MemoryOpenCoordinator
) : ViewModel() {

    private val _uiState = MutableStateFlow(FaceInsightsUiState(knownPeople = careToolkitRepository.getKnownPeople()))
    val uiState: StateFlow<FaceInsightsUiState> = _uiState.asStateFlow()

    fun applyPendingMemoryOpen() {
        memoryOpenCoordinator.consume()?.let { openFromMemory(it) }
    }

    private fun openFromMemory(payload: MemoryOpenPayload) {
        _uiState.value = _uiState.value.copy(
            backendSummary = payload.text.trim().ifBlank { payload.contextLabel() },
            backendError = null,
            moodHint = payload.title ?: "Opened from memories"
        )
    }

    private var lastVectorAtMs: Long = 0L
    private var recognizeJob: Job? = null
    private val recognizeMutex = Mutex()
    private var displayedIdentityName: String? = null
    private val enrollmentBuffer = EnrollmentBuffer()
    private val matchConfirmation = FaceMatchConfirmation()

    private companion object {
        const val THROTTLE_MS = 450L
    }

    init {
        viewModelScope.launch {
            memoryRepository.observeRecentFaceMemories(10).collectLatest { list ->
                _uiState.value = _uiState.value.copy(recentFaceMemories = list)
            }
        }
        viewModelScope.launch {
            val wiped = careToolkitRepository.migrateFaceDescriptorSchema(FaceDescriptor.SCHEMA_VERSION)
            careToolkitRepository.syncFaceProfilesFromServer()
            pruneInvalidFaceProfiles()
            if (wiped) {
                _uiState.value = _uiState.value.copy(
                    enrollSuccess = null,
                    enrollmentError = "Face templates were reset — please re-enroll each person."
                )
            }
            refreshToolkitSnapshot()
        }
    }

    fun onCameraPreviewReady() {
        _uiState.value = _uiState.value.copy(liveMode = LiveFaceUiMode.NoFace)
    }

    fun analyzeWithBackend(imageFile: File, mimeType: String, contextHint: String, onFaceCount: (Int, String) -> Unit) {
        _uiState.value = _uiState.value.copy(isAnalyzing = true, backendError = null)
        viewModelScope.launch {
            try {
                val resp = faceRepository.analyzeFace(imageFile, mimeType, contextHint)
                val observations = if (resp.observations.isEmpty()) "" else resp.observations.joinToString("; ")
                val summary = buildString {
                    append("Faces: ${resp.faceCount}. Mood: ${resp.dominantMood}. ")
                    if (observations.isNotBlank()) append(observations)
                    if (resp.careSuggestion.isNotBlank()) {
                        append("\n")
                        append(resp.careSuggestion)
                    }
                }
                _uiState.value = _uiState.value.copy(isAnalyzing = false, backendSummary = summary.trim())
                onFaceCount(resp.faceCount, resp.dominantMood)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false,
                    backendError = e.message ?: "Could not analyze photo"
                )
            }
        }
    }

    fun saveResult(faceCount: Int, emotionHint: String, personNote: String) {
        viewModelScope.launch {
            val extra = personNote.trim().takeIf { it.isNotEmpty() }?.let { ", note=$it" } ?: ""
            val text = "Face analysis: faces=$faceCount, hint=$emotionHint$extra"
            memoryRepository.saveTextMemory(
                text = text,
                type = "FACE_ANALYSIS",
                title = "Face Insight",
                mood = emotionHint,
                tags = listOf("face", "insight", "person-note")
            )
        }
    }

    fun addEnrollmentSample(vector: List<Float>) {
        val arr = FaceDescriptor.toIdentityFloatArray(vector)
        if (arr.isEmpty()) return
        enrollmentBuffer.addSample(arr)
        _uiState.value = _uiState.value.copy(
            enrollmentSamplesCollected = enrollmentBuffer.sampleCount,
            enrollmentError = null
        )
    }

    fun clearEnrollmentSamples() {
        enrollmentBuffer.clear()
        _uiState.value = _uiState.value.copy(enrollmentSamplesCollected = 0)
    }

    fun commitEnrollment(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        if (!enrollmentBuffer.isReady()) {
            _uiState.value = _uiState.value.copy(
                enrollmentError = "Hold still facing the camera — need at least " +
                    "${EnrollmentBuffer.MIN_READY} clear frames (have ${enrollmentBuffer.sampleCount}).",
                enrollSuccess = null
            )
            return
        }
        val template = enrollmentBuffer.buildTemplate()
        if (template.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                enrollmentError = "Could not build face template. Try again with better lighting.",
                enrollSuccess = null
            )
            return
        }
        enrollmentBuffer.clear()
        _uiState.value = _uiState.value.copy(enrollmentSamplesCollected = 0)
        enrollKnownFace(trimmed, template.toList())
    }

    fun enrollKnownFace(name: String, vector: List<Float>) {
        if (name.isBlank() || vector.isEmpty()) return
        val identity = FaceDescriptor.toIdentityFloatArray(vector).toList()
        if (identity.isEmpty()) return
        viewModelScope.launch {
            val existing = runCatching { careToolkitRepository.loadFaceProfiles() }.getOrElse { emptyList() }
            val templateArr = FaceDescriptor.toIdentityFloatArray(identity)
            val isDuplicate = existing.any { profile ->
                val stored = FaceDescriptor.toIdentityFloatArray(profile.vector)
                stored.isNotEmpty() &&
                    FaceIdentityMatcher.cosineBetween(templateArr, stored) >=
                    MatcherThresholds.DUPLICATE_ENROLL_MIN_COSINE
            }
            if (isDuplicate) {
                _uiState.value = _uiState.value.copy(
                    enrollmentError = "A similar face is already saved. Remove the old profile first.",
                    enrollSuccess = null
                )
                return@launch
            }

            runCatching {
                careToolkitRepository.replaceFaceProfile(name = name.trim(), vector = identity)
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    enrollmentError = e.message ?: "Could not save face",
                    enrollSuccess = null
                )
            }.onSuccess {
                refreshToolkitSnapshot()
                val trimmed = name.trim()
                matchConfirmation.reset()
                displayedIdentityName = null
                _uiState.value = _uiState.value.copy(
                    enrollmentError = null,
                    enrollSuccess = "Saved as $trimmed — have someone else test (should stay Unknown)",
                    liveMode = LiveFaceUiMode.UnknownFace,
                    liveLabel = "Unknown",
                    liveConfidenceWords = "Not saved — add a name below",
                    enrollmentSamplesCollected = 0
                )
            }
        }
    }

    fun clearEnrollBanner() {
        _uiState.value = _uiState.value.copy(enrollSuccess = null, enrollmentError = null)
    }

    fun deleteSavedFace(id: String) {
        viewModelScope.launch {
            runCatching { careToolkitRepository.deleteFaceProfile(id) }
            refreshToolkitSnapshot()
        }
    }

    fun refreshToolkitSnapshot() {
        viewModelScope.launch {
            val faces = runCatching { careToolkitRepository.loadFaceProfiles() }.getOrElse { emptyList() }
            _uiState.value = _uiState.value.copy(
                profilesCount = faces.size,
                savedFaces = faces,
                knownPeople = careToolkitRepository.getKnownPeople()
            )
        }
    }

    /**
     * Clears in-flight recognition. Pass [restartCamera] = true after disposing the preview
     * so the UI shows loading until [onCameraPreviewReady] runs again.
     */
    fun resetLiveRecognition(
        restartCamera: Boolean = false,
        keepScanning: Boolean = false,
        clearEnrollmentLock: Boolean = false
    ) {
        recognizeJob?.cancel()
        matchConfirmation.reset()
        displayedIdentityName = null
        if (clearEnrollmentLock) {
            enrollmentBuffer.clear()
        }
        val mode = when {
            restartCamera -> LiveFaceUiMode.CameraLoading
            keepScanning -> LiveFaceUiMode.UnknownFace
            else -> LiveFaceUiMode.Idle
        }
        _uiState.value = _uiState.value.copy(
            liveMode = mode,
            liveLabel = "Unknown",
            liveConfidenceWords = if (keepScanning) "Not saved — add a name below" else "Unknown"
        )
    }

    fun setLiveCameraLoading() {
        _uiState.value = _uiState.value.copy(liveMode = LiveFaceUiMode.CameraLoading)
    }

    fun showFaceModelError(message: String) {
        _uiState.value = _uiState.value.copy(
            liveMode = LiveFaceUiMode.Idle,
            enrollmentError = message,
            enrollSuccess = null
        )
    }

    fun updateMoodHint(hint: String) {
        _uiState.value = _uiState.value.copy(moodHint = hint)
    }

    /**
     * On-device recognition: MobileFaceNet cosine match on stored L2-normalized templates.
     */
    fun recognizeFromVector(vector: List<Float>) {
        if (vector.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                liveMode = LiveFaceUiMode.NoFace,
                liveLabel = "Unknown",
                liveConfidenceWords = "Unknown"
            )
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (now - lastVectorAtMs < THROTTLE_MS) return
        lastVectorAtMs = now

        recognizeJob?.cancel()
        recognizeJob = viewModelScope.launch {
            recognizeMutex.withLock {
                withContext(Dispatchers.Main.immediate) {
                    _uiState.value = _uiState.value.copy(liveMode = LiveFaceUiMode.Identifying)
                }

                val allProfiles = withContext(Dispatchers.IO) {
                    runCatching { careToolkitRepository.loadFaceProfiles() }.getOrElse { emptyList() }
                }

                val queryArr = FaceDescriptor.toIdentityFloatArray(vector)
                if (queryArr.isEmpty()) {
                    applyUnknownUi(allProfiles.size)
                    return@withLock
                }

                if (allProfiles.isEmpty()) {
                    applyUnknownUi(0)
                    return@withLock
                }

                val result = withContext(Dispatchers.Default) {
                    FaceIdentityMatcher.identifyFace(queryArr, allProfiles)
                }

                withContext(Dispatchers.Main.immediate) {
                    when (result) {
                        is IdentityResult.PossibleMatch -> {
                            matchConfirmation.reset()
                            displayedIdentityName = null
                            val pct = (result.cosineSimilarity * 100).toInt().coerceIn(0, 99)
                            _uiState.value = _uiState.value.copy(
                                liveMode = LiveFaceUiMode.KnownLow,
                                liveLabel = "Possible: ${result.name}",
                                liveConfidenceWords = "Similarity $pct% — hold still to confirm",
                                profilesCount = allProfiles.size
                            )
                        }
                        else -> {
                            val confirmed = matchConfirmation.observe(result)
                            when {
                                confirmed != null -> {
                                    if (displayedIdentityName != null && displayedIdentityName != confirmed) {
                                        matchConfirmation.reset()
                                        applyUnknownUi(allProfiles.size)
                                    } else {
                                        displayedIdentityName = confirmed
                                        _uiState.value = _uiState.value.copy(
                                            liveMode = LiveFaceUiMode.KnownHigh,
                                            liveLabel = confirmed,
                                            liveConfidenceWords = "Recognized",
                                            profilesCount = allProfiles.size
                                        )
                                    }
                                }
                                result is IdentityResult.Identified -> {
                                    _uiState.value = _uiState.value.copy(
                                        liveMode = LiveFaceUiMode.Identifying,
                                        liveLabel = "Unknown",
                                        liveConfidenceWords = "Checking… " +
                                            "${matchConfirmation.confirmStreak}/${matchConfirmation.confirmRequired}",
                                        profilesCount = allProfiles.size
                                    )
                                }
                                else -> applyUnknownUi(allProfiles.size)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun applyUnknownUi(profileCount: Int) {
        matchConfirmation.reset()
        displayedIdentityName = null
        _uiState.value = _uiState.value.copy(
            liveMode = LiveFaceUiMode.UnknownFace,
            liveLabel = "Unknown",
            liveConfidenceWords = "Not saved — add a name below",
            profilesCount = profileCount
        )
    }

    /** Drop legacy/wrong-dimension templates so they cannot false-match. */
    fun pruneInvalidFaceProfiles() {
        viewModelScope.launch {
            val all = runCatching { careToolkitRepository.loadFaceProfiles() }.getOrElse { emptyList() }
            val invalid = all.filter {
                FaceDescriptor.toIdentityFloatArray(it.vector).isEmpty()
            }
            invalid.forEach { runCatching { careToolkitRepository.deleteFaceProfile(it.id) } }
            if (invalid.isNotEmpty()) refreshToolkitSnapshot()
        }
    }
}
