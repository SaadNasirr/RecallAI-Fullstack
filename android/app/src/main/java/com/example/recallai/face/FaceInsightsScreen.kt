package com.example.recallai.face

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.recallai.data.FaceProfileItem
import com.example.recallai.ui.components.RecallTopBar
import com.example.recallai.ui.components.TonalActionButton
import com.example.recallai.ui.dashboard.MindcareColors
import com.example.recallai.ui.dashboard.MindcareGradientBackground
import com.example.recallai.ui.dashboard.MindcarePrimaryPillButton
import com.example.recallai.ui.dashboard.MindcareSectionTitle
import com.example.recallai.ui.components.MemoryMedalTimelineCard
import com.example.recallai.ui.components.SectionTitle
import com.example.recallai.ui.components.formatMemoryInstantMs
import com.example.recallai.ui.components.memoryAccentForType
import com.example.recallai.ui.components.memoryTypeChipLabel
import com.example.recallai.voice.RemoteSpeechPlayer
import com.example.recallai.di.FaceNetEmbedderEntryPoint
import com.google.mlkit.vision.common.InputImage
import dagger.hilt.android.EntryPointAccessors
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
@Suppress("UNUSED_PARAMETER")
fun FaceInsightsScreen(
    onBack: () -> Unit,
    onNavigateHome: () -> Unit = {},
    onNavigateChat: () -> Unit = {},
    onNavigateFace: () -> Unit = {},
    onNavigateMemories: () -> Unit = {},
    onNavigateRecall: () -> Unit = {},
    onNavigateToPeopleBook: () -> Unit = {},
    viewModel: FaceInsightsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ui by viewModel.uiState.collectAsState()
    val faceEmbedder = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            FaceNetEmbedderEntryPoint::class.java
        ).faceNetEmbedder()
    }

    var showSavedList by remember { mutableStateOf(false) }
    var liveScan by remember { mutableStateOf(false) }
    var camDenied by remember { mutableStateOf(false) }
    var overlayBounds by remember { mutableStateOf<FaceBoundsNorm?>(null) }
    var liveVector by remember { mutableStateOf<List<Float>>(emptyList()) }
    var enrollName by remember { mutableStateOf("") }
    var lastAnnounced by remember { mutableStateOf("") }
    var stuckIdentifying by remember { mutableStateOf(false) }
    var cameraSessionKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        viewModel.applyPendingMemoryOpen()
    }

    LaunchedEffect(ui.liveMode, liveScan) {
        if (!liveScan || ui.liveMode != LiveFaceUiMode.Identifying) {
            stuckIdentifying = false
            return@LaunchedEffect
        }
        delay(3000)
        stuckIdentifying = liveScan && ui.liveMode == LiveFaceUiMode.Identifying
    }

    LaunchedEffect(ui.enrollSuccess) {
        if (ui.enrollSuccess != null) {
            delay(4500)
            viewModel.clearEnrollBanner()
        }
    }

    var faceCount by remember { mutableIntStateOf(0) }
    var hasPhotoAnalysis by remember { mutableStateOf(false) }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var selectedMime by remember { mutableStateOf("image/jpeg") }
    var isPreparingImage by remember { mutableStateOf(false) }
    var personNote by remember { mutableStateOf("") }

    val camLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { ok ->
        if (ok) {
            camDenied = false
            viewModel.setLiveCameraLoading()
            liveScan = true
        } else {
            camDenied = true
            liveScan = true
        }
    }

    LaunchedEffect(ui.liveLabel, liveScan) {
        if (!liveScan) return@LaunchedEffect
        val label = ui.liveLabel.trim().ifBlank { "Unknown" }
        delay(420)
        if (!liveScan) return@LaunchedEffect
        if (ui.liveLabel.trim().ifBlank { "Unknown" } != label) return@LaunchedEffect
        val key = if (label.equals("Unknown", ignoreCase = true)) "u" else "n:${label.lowercase()}"
        if (key == lastAnnounced) return@LaunchedEffect
        lastAnnounced = key
        val phrase = if (label.equals("Unknown", ignoreCase = true)) {
            "Unknown person."
        } else {
            "Hello, $label."
        }
        scope.launch { runCatching { RemoteSpeechPlayer.speak(context, phrase) } }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isPreparingImage = true
            runCatching {
                val (file, mime) = withContext(Dispatchers.IO) { uriToTempFile(context, uri) }
                selectedFile = file
                selectedMime = mime
                viewModel.analyzeWithBackend(file, mime, "Wellbeing face insight") { count, mood ->
                    faceCount = count
                    viewModel.updateMoodHint(moodHintFromBackend(mood, count))
                }
                val detector = FaceDetection.getClient(
                    FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                        .build()
                )
                val image = InputImage.fromFilePath(context, uri)
                detector.process(image)
                    .addOnSuccessListener { faces ->
                        faceCount = faces.size
                        hasPhotoAnalysis = true
                        viewModel.updateMoodHint(moodHintFromLocal(faces))
                    }
                    .addOnFailureListener {
                        viewModel.updateMoodHint("Could not read mood")
                    }
            }.onFailure {
                viewModel.updateMoodHint("Photo failed")
            }
            isPreparingImage = false
        }
    }

    fun openLiveCamera() {
        if (!faceEmbedder.isReady()) {
            viewModel.showFaceModelError(
                faceEmbedder.loadErrorMessage()
                    ?: "Face recognition model is missing. Reinstall the app."
            )
            return
        }
        val ok = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (ok) {
            camDenied = false
            viewModel.setLiveCameraLoading()
            liveScan = true
        } else {
            camLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val isUnknown = ui.liveLabel.equals("Unknown", ignoreCase = true) ||
        ui.liveMode == LiveFaceUiMode.UnknownFace
    val recognizedNow = liveScan && !isUnknown && ui.liveMode == LiveFaceUiMode.KnownHigh
    val enrollingUnknown = isUnknown && liveScan && ui.liveMode != LiveFaceUiMode.NoFace

    LaunchedEffect(enrollingUnknown, liveVector) {
        if (enrollingUnknown && liveVector.isNotEmpty()) {
            viewModel.addEnrollmentSample(liveVector)
        }
    }

    LaunchedEffect(enrollingUnknown) {
        if (!enrollingUnknown) {
            viewModel.clearEnrollmentSamples()
        }
    }

    MindcareGradientBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                RecallTopBar(
                    title = if (showSavedList) "Saved faces" else "Face insights",
                    onBack = {
                        if (showSavedList) showSavedList = false else onBack()
                    }
                )
            }
        ) { padding ->
            if (showSavedList) {
                SavedFacesListPanel(
                    faces = ui.savedFaces,
                    onDelete = { viewModel.deleteSavedFace(it) },
                    onOpenCamera = {
                        showSavedList = false
                        openLiveCamera()
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp)
                        .navigationBarsPadding()
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    FaceMindcareWelcomeCard(
                        subtitle = when {
                            liveScan && recognizedNow -> "Recognized: ${ui.liveLabel}"
                            liveScan && isUnknown -> "Unknown face — save a name to remember"
                            liveScan -> "Point the camera at a face"
                            ui.profilesCount > 0 -> "${ui.profilesCount} saved · open camera to scan"
                            else -> "Recognize people and read mood from photos"
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FaceMindcareStatPill(
                            label = "Saved",
                            value = ui.profilesCount.toString(),
                            accent = MindcareColors.CardLavender,
                            modifier = Modifier.weight(1f)
                        )
                        FaceMindcareStatPill(
                            label = "Status",
                            value = when {
                                !liveScan -> "Ready"
                                ui.liveMode == LiveFaceUiMode.KnownHigh -> "Known"
                                ui.liveMode == LiveFaceUiMode.KnownLow -> "Maybe"
                                ui.liveMode == LiveFaceUiMode.Identifying -> "Scan"
                                else -> "New"
                            },
                            accent = faceStatusStyle(ui.liveMode, isUnknown, liveScan).surface,
                            modifier = Modifier.weight(1f)
                        )
                        FaceMindcareStatPill(
                            label = "Mood",
                            value = moodShortLabel(ui.moodHint),
                            accent = faceMoodVisual(ui.moodHint).surface,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (liveScan) {
                            MindcarePrimaryPillButton(
                                text = "Stop camera",
                                onClick = {
                                    liveScan = false
                                    camDenied = false
                                    overlayBounds = null
                                    lastAnnounced = ""
                                    RemoteSpeechPlayer.stop()
                                    viewModel.resetLiveRecognition()
                                },
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            MindcarePrimaryPillButton(
                                text = "Open camera",
                                onClick = { openLiveCamera() },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        IconButton(
                            onClick = {
                                viewModel.pruneInvalidFaceProfiles()
                                viewModel.refreshToolkitSnapshot()
                            },
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.9f))
                        ) {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = "Refresh faces",
                                tint = MindcareColors.Ink
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FaceMindcareActionChip(
                            label = "Saved",
                            icon = Icons.Filled.Person,
                            tint = MindcareColors.CardLavender,
                            onClick = { showSavedList = true }
                        )
                        FaceMindcareActionChip(
                            label = "Photo",
                            icon = Icons.Filled.Image,
                            tint = MindcareColors.CardMint,
                            onClick = { picker.launch("image/*") },
                            enabled = !isPreparingImage
                        )
                        FaceMindcareActionChip(
                            label = "People",
                            icon = Icons.Filled.Face,
                            tint = MindcareColors.CardPink,
                            onClick = onNavigateToPeopleBook
                        )
                    }

                    FaceSectionHeader(
                        title = if (liveScan) "Live scan" else "Camera",
                        badge = if (liveScan) "On-device" else null
                    )

                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(360.dp)
                    ) {
                        val h = maxHeight
                        val scanStyle = faceStatusStyle(ui.liveMode, isUnknown, liveScan)
                        FaceCameraViewport(
                            modifier = Modifier.fillMaxSize(),
                            isLive = liveScan && !camDenied
                        ) {
                            when {
                            !liveScan -> FaceCameraIdlePlaceholder()
                            camDenied -> FaceCameraPermissionCard(
                                onOpenSettings = {
                                    val i = android.content.Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                                    ).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                    context.startActivity(i)
                                }
                            )
                            else -> {
                                Box(Modifier.fillMaxSize()) {
                                    Surface(
                                        onClick = {
                                            liveScan = false
                                            camDenied = false
                                            overlayBounds = null
                                            lastAnnounced = ""
                                            RemoteSpeechPlayer.stop()
                                            viewModel.resetLiveRecognition()
                                        },
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(10.dp),
                                        shape = CircleShape,
                                        color = Color.Black.copy(alpha = 0.5f)
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Close camera",
                                            tint = Color.White,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                    key(cameraSessionKey) {
                                        LiveFaceScannerView(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(h),
                                            embedder = faceEmbedder,
                                            onFaceDetected = { count, vector, bounds ->
                                                faceCount = count
                                                overlayBounds = bounds
                                                if (vector.isEmpty()) {
                                                    viewModel.recognizeFromVector(emptyList())
                                                } else {
                                                    liveVector = vector
                                                    viewModel.recognizeFromVector(vector)
                                                }
                                            },
                                            onCameraReady = { viewModel.onCameraPreviewReady() }
                                        )
                                    }
                                    if (ui.liveMode == LiveFaceUiMode.CameraLoading) {
                                        FaceCameraLoadingOverlay(Modifier.fillMaxSize())
                                    }
                                    FaceOverlayFrame(
                                        bounds = overlayBounds,
                                        label = when (ui.liveMode) {
                                            LiveFaceUiMode.NoFace -> "No face detected"
                                            LiveFaceUiMode.Identifying -> "Identifying…"
                                            else -> ui.liveLabel
                                        },
                                        style = scanStyle,
                                        pulseIdentifying = ui.liveMode == LiveFaceUiMode.Identifying,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = liveScan,
                        enter = fadeIn(tween(400)) + slideInVertically(
                            initialOffsetY = { it / 8 },
                            animationSpec = tween(450)
                        )
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        FaceSectionHeader(title = "Recognition", badge = "Private")
                        FaceLiveStatusCard(
                            liveScan = liveScan,
                            ui = ui,
                            isUnknown = isUnknown
                        )

                        ui.enrollSuccess?.let { msg ->
                            FaceFeedbackBanner(message = msg, isError = false)
                        }
                        ui.enrollmentError?.let { msg ->
                            FaceFeedbackBanner(
                                message = msg,
                                isError = true,
                                onDismiss = { viewModel.clearEnrollBanner() }
                            )
                        }
                        if (stuckIdentifying) {
                            TonalActionButton(
                                text = "Try again",
                                onClick = {
                                    stuckIdentifying = false
                                    cameraSessionKey++
                                    viewModel.resetLiveRecognition(restartCamera = true)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (isUnknown && ui.liveMode != LiveFaceUiMode.NoFace) {
                            FaceEnrollmentCard(
                                enrollName = enrollName,
                                onNameChange = { enrollName = it },
                                samplesCollected = ui.enrollmentSamplesCollected,
                                minSamples = EnrollmentBuffer.MIN_READY,
                                onSave = {
                                    val n = enrollName.trim()
                                    if (n.isNotBlank()) {
                                        viewModel.commitEnrollment(n)
                                        enrollName = ""
                                        lastAnnounced = ""
                                    }
                                }
                            )
                        }

                        if (recognizedNow) {
                            TonalActionButton(
                                text = "Wrong person — mark unknown",
                                onClick = {
                                    enrollName = ""
                                    lastAnnounced = ""
                                    viewModel.resetLiveRecognition(
                                        keepScanning = true,
                                        clearEnrollmentLock = true
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        }
                    }

                    FaceMoodHeroCard(
                        moodHint = ui.moodHint,
                        isAnalyzing = ui.isAnalyzing
                    )
                    FaceMindcareAnalysisPanel(
                        summary = ui.backendSummary,
                        error = ui.backendError
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        color = Color.White,
                        shadowElevation = 3.dp
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        MindcareSectionTitle("Photo analysis")
                        Spacer(Modifier.height(2.dp))
                        OutlinedTextField(
                            value = personNote,
                            onValueChange = { personNote = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Note (optional)") },
                            minLines = 2,
                            maxLines = 3,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MindcareColors.Ink.copy(alpha = 0.15f)
                            )
                        )
                        if (ui.knownPeople.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                ui.knownPeople.take(6).forEach { person ->
                                    AssistChip(
                                        onClick = {
                                            personNote = buildString {
                                                append(person.name)
                                                if (person.relation.isNotBlank()) {
                                                    append(" (${person.relation})")
                                                }
                                            }
                                        },
                                        label = {
                                            Text(
                                                person.name,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TonalActionButton(
                                text = "Choose photo",
                                onClick = { picker.launch("image/*") },
                                modifier = Modifier.weight(1f),
                                enabled = !isPreparingImage
                            )
                            MindcarePrimaryPillButton(
                                text = if (ui.isAnalyzing) "Analyzing…" else "Analyze",
                                onClick = {
                                    if (selectedFile == null || ui.isAnalyzing) return@MindcarePrimaryPillButton
                                    val file = selectedFile!!
                                    viewModel.analyzeWithBackend(
                                        file,
                                        selectedMime,
                                        "Wellbeing face insight"
                                    ) { c, m ->
                                        faceCount = c
                                        viewModel.updateMoodHint(moodHintFromBackend(m, c))
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        MindcarePrimaryPillButton(
                            text = "Save to memories",
                            onClick = {
                                if (hasPhotoAnalysis || faceCount > 0) {
                                    viewModel.saveResult(faceCount, ui.moodHint, personNote)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        }
                    }

                    if (ui.recentFaceMemories.size > 1) {
                        SectionTitle("Recent")
                        ui.recentFaceMemories.drop(1).take(5).forEachIndexed { index, mem ->
                            MemoryMedalTimelineCard(
                                memoryId = mem.id,
                                rankLabel = "Log ${index + 1}",
                                typeLabel = memoryTypeChipLabel(mem.type),
                                title = mem.title ?: "Face",
                                preview = mem.text,
                                createdAt = mem.createdAt,
                                accentColor = memoryAccentForType(mem.type),
                                onOpenTimeline = onNavigateMemories,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(88.dp))
                }
            }
        }
    }
}

private fun moodShortLabel(hint: String): String = when {
    hint.contains("Positive", ignoreCase = true) -> "Good"
    hint.contains("Neutral", ignoreCase = true) -> "Calm"
    hint.contains("No photo", ignoreCase = true) -> "—"
    hint.contains("No face", ignoreCase = true) -> "—"
    else -> "OK"
}

private fun moodHintFromBackend(mood: String, count: Int): String {
    return if (count <= 0) "No face in photo" else mood
}

private fun moodHintFromLocal(faces: List<com.google.mlkit.vision.face.Face>): String {
    if (faces.isEmpty()) return "No face in photo"
    return if (faces.any { (it.smilingProbability ?: 0f) > 0.55f }) "Positive" else "Neutral"
}

@Composable
private fun SavedFacesListPanel(
    faces: List<FaceProfileItem>,
    onDelete: (String) -> Unit,
    onOpenCamera: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        FaceMindcareWelcomeCard(
            subtitle = if (faces.isEmpty()) {
                "Enroll people from the live camera"
            } else {
                "${faces.size} people the app can recognize on this device"
            }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FaceMindcareStatPill(
                label = "People",
                value = faces.size.toString(),
                accent = MindcareColors.CardLavender,
                modifier = Modifier.weight(1f)
            )
            MindcarePrimaryPillButton(
                text = "Open camera",
                onClick = onOpenCamera,
                modifier = Modifier.weight(2f)
            )
        }

        FaceSectionHeader(
            title = "Enrolled",
            badge = if (faces.isNotEmpty()) "${faces.size}" else null
        )

        if (faces.isEmpty()) {
            FaceEmptySavedList()
        } else {
            faces.forEach { f ->
                FaceSavedPersonCard(face = f, onDelete = { onDelete(f.id) })
            }
        }

        Spacer(Modifier.height(48.dp))
    }
}

private fun uriToTempFile(context: android.content.Context, uri: Uri): Pair<File, String> {
    val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
    val ext = mime.substringAfterLast('/').ifBlank { "jpg" }
    val input = context.contentResolver.openInputStream(uri)
        ?: throw IllegalStateException("Failed to open image")
    val bytes = input.use { it.readBytes() }
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    val outFile = File(context.cacheDir, "face_${System.currentTimeMillis()}.$ext")
    FileOutputStream(outFile).use { out ->
        if (bitmap != null) {
            bitmap.downscale(960).compress(Bitmap.CompressFormat.JPEG, 74, out)
        } else {
            out.write(bytes)
        }
        out.flush()
    }
    return outFile to mime
}

private fun Bitmap.downscale(maxDim: Int): Bitmap {
    val largest = maxOf(width, height)
    if (largest <= maxDim) return this
    val scale = maxDim.toFloat() / largest.toFloat()
    val dstW = (width * scale).toInt().coerceAtLeast(1)
    val dstH = (height * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(this, dstW, dstH, true)
}
