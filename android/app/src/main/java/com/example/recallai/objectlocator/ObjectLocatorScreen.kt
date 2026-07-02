@file:Suppress("UNUSED_PARAMETER")

package com.example.recallai.objectlocator

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.recallai.R
import com.example.recallai.care.CareActionTile
import com.example.recallai.care.CareStatusChip
import com.example.recallai.care.CareSummaryTile
import com.example.recallai.ui.components.AnimatedAssistChip
import com.example.recallai.ui.components.AppBackdrop
import com.example.recallai.ui.components.GlassCard
import com.example.recallai.ui.components.HeroHeaderCard
import com.example.recallai.ui.components.InAppPhotoCamera
import com.example.recallai.ui.components.ObjectFoundHeroCard
import com.example.recallai.ui.components.PrimaryActionButton
import com.example.recallai.ui.components.ReadableAnswerPanel
import com.example.recallai.ui.components.RecallTopBar
import com.example.recallai.ui.components.SectionTitle
import com.example.recallai.ui.components.formatMemoryInstantMs
import com.example.recallai.voice.RemoteSpeechPlayer
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ObjectLocatorScreen(
    onBack: () -> Unit,
    onNavigateHome: () -> Unit = {},
    onNavigateChat: () -> Unit = {},
    onNavigateFace: () -> Unit = {},
    onNavigateMemories: () -> Unit = {},
    onNavigateRecall: () -> Unit = {},
    viewModel: ObjectLocatorViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val isReadingAloud by RemoteSpeechPlayer.isReadingAloud.collectAsState()
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.applyPendingMemoryOpen()
    }

    var selectedFile by remember { mutableStateOf<File?>(null) }
    var selectedMime by remember { mutableStateOf("image/jpeg") }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isPreparingImage by remember { mutableStateOf(false) }
    var showPickError by remember { mutableStateOf<String?>(null) }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        showPickError = null
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isPreparingImage = true
            try {
                val (file, mime, bitmap) = withContext(Dispatchers.IO) { uriToImageFile(context, uri) }
                selectedFile = file
                selectedMime = mime
                previewBitmap = bitmap
            } catch (e: Exception) {
                showPickError = e.message ?: "Failed to load image"
            } finally {
                isPreparingImage = false
            }
        }
    }

    var showInAppCamera by remember { mutableStateOf(false) }
    var pendingCameraLaunch by remember { mutableStateOf(false) }

    fun applyCapturedBitmap(bitmap: Bitmap) {
        showPickError = null
        scope.launch {
            isPreparingImage = true
            try {
                val file = withContext(Dispatchers.IO) {
                    createCaptureTempFile(context.cacheDir).also { it.outputBitmapToJpeg(bitmap) }
                }
                selectedFile = file
                selectedMime = "image/jpeg"
                previewBitmap = bitmap
            } finally {
                isPreparingImage = false
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            showPickError = "Camera permission is required to capture photos."
        } else if (pendingCameraLaunch) {
            pendingCameraLaunch = false
            showInAppCamera = true
        }
    }

    fun openCamera() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) {
            pendingCameraLaunch = false
            showInAppCamera = true
        } else {
            pendingCameraLaunch = true
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val recentCount = state.recentObjectSaves.size + if (state.lastSavedObject != null) 1 else 0
    val hasPhoto = selectedFile != null && previewBitmap != null
    val hasResult = !state.responseText.isNullOrBlank()
    val statusLabel = when {
        state.isLoading -> "Scan"
        state.error != null -> "Error"
        hasResult -> "Found"
        else -> "Ready"
    }
    val statusAccent = when {
        state.isLoading -> Color(0xFF1565C0)
        state.error != null -> MaterialTheme.colorScheme.error
        hasResult -> Color(0xFF2E7D32)
        else -> Color(0xFF546E7A)
    }

    val feedbackMessage = showPickError ?: state.info
    val feedbackIsError = showPickError != null

    AppBackdrop {
        Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.08f),
            topBar = {
                RecallTopBar(
                    title = "Object intelligence",
                    onBack = onBack
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Spacer(Modifier.height(4.dp))

                HeroHeaderCard(
                    title = "Object intelligence",
                    subtitle = when {
                        state.isLoading -> "Analyzing your photo…"
                        hasResult -> "Result ready — read, copy, or save below"
                        hasPhoto -> "Add what you are looking for, then tap Locate"
                        recentCount > 0 -> "$recentCount past scans · add a photo to search again"
                        else -> "Describe an object and scan a photo to find it"
                    },
                    illustrationRes = R.drawable.img_tool_object,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CareSummaryTile(
                        label = "History",
                        value = recentCount.toString(),
                        accent = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    CareSummaryTile(
                        label = "Status",
                        value = statusLabel,
                        accent = statusAccent,
                        modifier = Modifier.weight(1f)
                    )
                    CareSummaryTile(
                        label = "Photo",
                        value = if (hasPhoto) "Yes" else "No",
                        accent = if (hasPhoto) Color(0xFF2E7D32) else Color(0xFF757575),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CareActionTile(
                        label = "Gallery",
                        icon = Icons.Filled.Image,
                        onClick = { pickImageLauncher.launch("image/*") },
                        modifier = Modifier.weight(1f),
                        enabled = !state.isLoading && !isPreparingImage
                    )
                    CareActionTile(
                        label = "Camera",
                        icon = Icons.Filled.CameraAlt,
                        onClick = { openCamera() },
                        modifier = Modifier.weight(1f),
                        enabled = !state.isLoading && !isPreparingImage
                    )
                    CareActionTile(
                        label = "Timeline",
                        icon = Icons.Filled.Timeline,
                        onClick = onNavigateMemories,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (!feedbackMessage.isNullOrBlank()) {
                    LocatorFeedbackBanner(
                        message = feedbackMessage,
                        isError = feedbackIsError
                    )
                }

                SectionTitle("Describe & scan")

                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = viewModel::onQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("What are you looking for?") },
                        placeholder = { Text("e.g. Where are my glasses?") },
                        leadingIcon = {
                            Icon(Icons.Filled.Search, contentDescription = null)
                        },
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Photo",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                RoundedCornerShape(16.dp)
                            )
                    ) {
                        if (previewBitmap != null) {
                            Image(
                                bitmap = previewBitmap!!.asImageBitmap(),
                                contentDescription = "Current photo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                    ),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Outlined.ImageSearch,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Add a photo to scan",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "Use Gallery or Camera above",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (isPreparingImage) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                            Text("Preparing photo…", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    PrimaryActionButton(
                        text = when {
                            state.isLoading -> "Locating…"
                            else -> "Locate object"
                        },
                        onClick = {
                            val file = selectedFile ?: return@PrimaryActionButton
                            viewModel.analyzeImage(imageFile = file, mimeType = selectedMime)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = selectedFile != null && !state.isLoading && !isPreparingImage
                    )
                }

                SectionTitle("Answer")

                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    when {
                        state.isLoading -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 140.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator()
                                    Spacer(Modifier.height(10.dp))
                                    Text(
                                        "Analyzing your photo…",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        state.error != null -> {
                            LocatorAnswerErrorCard(message = state.error ?: "")
                        }

                        hasResult -> {
                            if (!state.textQuery.isNullOrBlank()) {
                                CareStatusChip(
                                    text = "Query: ${state.textQuery}",
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(Modifier.height(10.dp))
                            }
                            ReadableAnswerPanel(
                                title = "Result",
                                text = state.responseText ?: "",
                                minHeight = 120.dp,
                                maxHeight = 320.dp
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AnimatedAssistChip(
                                    label = "Copy",
                                    onClick = {
                                        val txt = state.responseText.orEmpty()
                                        if (txt.isNotBlank()) {
                                            clipboard.setText(AnnotatedString(txt))
                                        }
                                    },
                                    enabled = hasResult
                                )
                                AnimatedAssistChip(
                                    label = if (isReadingAloud) "Stop audio" else "Read aloud",
                                    onClick = {
                                        if (isReadingAloud) {
                                            RemoteSpeechPlayer.stop()
                                        } else {
                                            val txt = state.responseText.orEmpty()
                                            if (txt.isNotBlank()) {
                                                scope.launch {
                                                    runCatching {
                                                        RemoteSpeechPlayer.speak(context, txt)
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    enabled = isReadingAloud || hasResult
                                )
                                AnimatedAssistChip(
                                    label = "Save",
                                    onClick = { viewModel.saveCurrentResultAsImportant() },
                                    enabled = hasResult
                                )
                            }
                        }

                        else -> {
                            LocatorAnswerEmptyPlaceholder()
                        }
                    }
                }

                state.lastSavedObject?.let { last ->
                    SectionTitle("Last scan")
                    ObjectFoundHeroCard(
                        badgeLabel = "Last scan",
                        timeLabel = formatMemoryInstantMs(last.createdAt),
                        headline = last.headline,
                        body = last.detail,
                        caption = "",
                        imagePath = last.imagePath,
                        locationHint = "Device",
                        onReadAloud = {
                            scope.launch {
                                runCatching { RemoteSpeechPlayer.speak(context, last.detail) }
                            }
                        },
                        onStopAudio = { RemoteSpeechPlayer.stop() },
                        isSpeaking = isReadingAloud
                    )
                }

                if (state.recentObjectSaves.isNotEmpty()) {
                    SectionTitle("Recent searches")
                    state.recentObjectSaves.forEach { item ->
                        LocatorRecentCard(item = item)
                    }
                }

                Spacer(Modifier.height(88.dp))
            }
        }

        if (showInAppCamera) {
            InAppPhotoCamera(
                onBack = { showInAppCamera = false },
                onPhotoCaptured = { bitmap ->
                    // Camera unbinds before this callback; close overlay then process on a worker thread.
                    showInAppCamera = false
                    applyCapturedBitmap(bitmap)
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        }
    }
}

@Composable
private fun LocatorFeedbackBanner(message: String, isError: Boolean) {
    val bg = if (isError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val fg = if (isError) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = bg
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = fg
        )
    }
}

@Composable
private fun LocatorRecentCard(item: SavedObjectInsight) {
    val cardShape = MaterialTheme.shapes.large
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = cardShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                    cardShape
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.ImageSearch,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.headline,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = item.detail.take(96),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = formatMemoryInstantMs(item.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun LocatorAnswerErrorCard(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(28.dp)
            )
            Column {
                Text(
                    "Couldn't analyze this photo",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 22.sp
                )
            }
        }
    }
}

@Composable
private fun LocatorAnswerEmptyPlaceholder() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Outlined.ImageSearch,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
            modifier = Modifier.size(40.dp)
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Results will appear here",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Describe what you need, add a clear photo, then tap Locate object.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp
        )
    }
}

private fun uriToImageFile(context: android.content.Context, uri: android.net.Uri): Triple<File, String, Bitmap?> {
    val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
    val ext = mime.substringAfterLast('/').ifBlank { "jpg" }

    val input = context.contentResolver.openInputStream(uri)
        ?: throw IllegalStateException("Failed to open image")

    input.use {
        val bytes = it.readBytes()
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

        val file = File(context.cacheDir, "locator_${System.currentTimeMillis()}.$ext")
        if (bitmap != null) {
            val normalized = bitmap.downscale(maxDim = 2048)
            FileOutputStream(file).use { out ->
                normalized.compress(Bitmap.CompressFormat.JPEG, 92, out)
                out.flush()
            }
        } else {
            FileOutputStream(file).use { out ->
                out.write(bytes)
                out.flush()
            }
        }

        return Triple(file, mime, bitmap)
    }
}

private fun File.outputBitmapToJpeg(bitmap: Bitmap) {
    FileOutputStream(this).use { out ->
        bitmap.downscale(maxDim = 2048).compress(Bitmap.CompressFormat.JPEG, 94, out)
        out.flush()
    }
}

private fun createCaptureTempFile(cacheDir: File): File {
    return File(cacheDir, "locator_${System.currentTimeMillis()}_capture.jpg").apply { createNewFile() }
}

private fun Bitmap.downscale(maxDim: Int): Bitmap {
    val srcW = width
    val srcH = height
    val largest = maxOf(srcW, srcH)
    if (largest <= maxDim) return this
    val scale = maxDim.toFloat() / largest.toFloat()
    val dstW = (srcW * scale).toInt().coerceAtLeast(1)
    val dstH = (srcH * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(this, dstW, dstH, true)
}
