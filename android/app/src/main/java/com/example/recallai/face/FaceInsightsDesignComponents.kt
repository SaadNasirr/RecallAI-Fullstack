package com.example.recallai.face

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.FaceRetouchingNatural
import androidx.compose.material.icons.outlined.Mood
import androidx.compose.material.icons.outlined.NoPhotography
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recallai.care.pairing.PairingAvatarInitials
import com.example.recallai.data.FaceProfileItem
import com.example.recallai.ui.components.GlassCard
import com.example.recallai.ui.components.PrimaryActionButton
import com.example.recallai.ui.components.TonalActionButton
import com.example.recallai.ui.dashboard.MindcareColors
import com.example.recallai.ui.dashboard.MindcareSectionTitle
import java.text.DateFormat
import java.util.Date

/** Face recognition + mood tokens aligned with mindcare dashboard palette. */
object FaceInsightsTheme {
    val KnownInk = Color(0xFF2E7D32)
    val KnownSurface = MindcareColors.CardMint
    val PossibleInk = Color(0xFF5C6BC0)
    val PossibleSurface = MindcareColors.CardLavender
    val UnknownInk = Color(0xFFE65100)
    val UnknownSurface = MindcareColors.CardPeach
    val ScanInk = Color(0xFF7B6CF5)
    val ScanSurface = MindcareColors.CardLavender
    val NeutralInk = MindcareColors.Ink.copy(alpha = 0.55f)
    val NeutralSurface = Color.White
    val MoodInk = MindcareColors.Ink
    val MoodSurface = MindcareColors.CardPink
    val PrimaryViolet = Color(0xFF7B6CF5)
    val CameraFrameGradient = Brush.linearGradient(
        colors = listOf(
            PrimaryViolet.copy(alpha = 0.7f),
            MindcareColors.MoodNeutral.copy(alpha = 0.5f),
            MindcareColors.CardMint.copy(alpha = 0.55f)
        )
    )
}

data class FaceStatusStyle(
    val accent: Color,
    val surface: Color,
    val ink: Color,
    val icon: ImageVector
)

fun faceStatusStyle(mode: LiveFaceUiMode, isUnknown: Boolean, liveScan: Boolean): FaceStatusStyle {
    if (!liveScan) {
        return FaceStatusStyle(
            accent = FaceInsightsTheme.NeutralInk,
            surface = FaceInsightsTheme.NeutralSurface,
            ink = FaceInsightsTheme.NeutralInk,
            icon = Icons.Outlined.FaceRetouchingNatural
        )
    }
    return when (mode) {
        LiveFaceUiMode.KnownHigh -> FaceStatusStyle(
            accent = FaceInsightsTheme.KnownInk,
            surface = FaceInsightsTheme.KnownSurface,
            ink = FaceInsightsTheme.KnownInk,
            icon = Icons.Filled.Verified
        )
        LiveFaceUiMode.KnownLow -> FaceStatusStyle(
            accent = FaceInsightsTheme.PossibleInk,
            surface = FaceInsightsTheme.PossibleSurface,
            ink = FaceInsightsTheme.PossibleInk,
            icon = Icons.Outlined.Radar
        )
        LiveFaceUiMode.UnknownFace -> FaceStatusStyle(
            accent = FaceInsightsTheme.UnknownInk,
            surface = FaceInsightsTheme.UnknownSurface,
            ink = FaceInsightsTheme.UnknownInk,
            icon = Icons.Filled.Person
        )
        LiveFaceUiMode.Identifying -> FaceStatusStyle(
            accent = FaceInsightsTheme.ScanInk,
            surface = FaceInsightsTheme.ScanSurface,
            ink = FaceInsightsTheme.ScanInk,
            icon = Icons.Outlined.Radar
        )
        LiveFaceUiMode.NoFace -> FaceStatusStyle(
            accent = FaceInsightsTheme.NeutralInk,
            surface = FaceInsightsTheme.NeutralSurface,
            ink = FaceInsightsTheme.NeutralInk,
            icon = Icons.Outlined.NoPhotography
        )
        else -> FaceStatusStyle(
            accent = if (isUnknown) FaceInsightsTheme.UnknownInk else FaceInsightsTheme.ScanInk,
            surface = if (isUnknown) FaceInsightsTheme.UnknownSurface else FaceInsightsTheme.ScanSurface,
            ink = if (isUnknown) FaceInsightsTheme.UnknownInk else FaceInsightsTheme.ScanInk,
            icon = Icons.Filled.Face
        )
    }
}

@Composable
fun FaceSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    badge: String? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        MindcareSectionTitle(title)
        badge?.let {
            Surface(
                color = MindcareColors.CardLavender,
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    text = it,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MindcareColors.Ink
                )
            }
        }
    }
}

@Composable
fun FaceFeedbackBanner(
    message: String,
    isError: Boolean,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null
) {
    val bg = if (isError) MaterialTheme.colorScheme.errorContainer
    else FaceInsightsTheme.KnownSurface
    val fg = if (isError) MaterialTheme.colorScheme.onErrorContainer
    else FaceInsightsTheme.KnownInk
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = bg
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isError) Icons.Outlined.NoPhotography else Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = fg
            )
            onDismiss?.let {
                IconButton(onClick = it, modifier = Modifier.size(32.dp)) {
                    Text("✕", color = fg, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
fun FaceCameraViewport(
    modifier: Modifier = Modifier,
    isLive: Boolean,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .then(
                if (isLive) {
                    Modifier.border(2.dp, FaceInsightsTheme.CameraFrameGradient, shape)
                } else {
                    Modifier.border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                        shape
                    )
                }
            )
    ) {
        Box(Modifier.fillMaxSize()) {
            content()
            if (isLive) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFFB91C1C).copy(alpha = 0.92f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                        Text(
                            "LIVE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FaceCameraIdlePlaceholder(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MindcareColors.CardLavender.copy(alpha = 0.55f),
                        Color.White
                    )
                )
            )
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.CameraAlt,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Camera preview",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Tap Open camera to scan faces on-device with MobileFaceNet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = MaterialTheme.typography.bodySmall.lineHeight
        )
    }
}

@Composable
fun FaceCameraPermissionCard(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FaceInsightsTheme.UnknownSurface.copy(alpha = 0.65f))
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Outlined.NoPhotography,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = FaceInsightsTheme.UnknownInk
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Camera access is off",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Allow camera in Settings to recognize people live.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        PrimaryActionButton(
            text = "Open settings",
            onClick = onOpenSettings,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun FaceLiveStatusCard(
    liveScan: Boolean,
    ui: FaceInsightsUiState,
    isUnknown: Boolean,
    modifier: Modifier = Modifier
) {
    val style = faceStatusStyle(ui.liveMode, isUnknown, liveScan)
    val statusTitle = when {
        ui.liveMode == LiveFaceUiMode.NoFace && liveScan -> "No face in frame"
        ui.liveMode == LiveFaceUiMode.Identifying && liveScan -> "Identifying…"
        ui.liveMode == LiveFaceUiMode.KnownLow && liveScan -> ui.liveLabel
        isUnknown && liveScan -> "Unknown person"
        !isUnknown && liveScan -> ui.liveLabel
        else -> "Ready to scan"
    }
    val subtitle = when {
        liveScan && isUnknown && ui.liveMode != LiveFaceUiMode.NoFace ->
            "Save a name to remember them on this device"
        liveScan && ui.liveMode == LiveFaceUiMode.KnownLow -> ui.liveConfidenceWords
        liveScan && !isUnknown -> ui.liveConfidenceWords
        liveScan -> ui.liveConfidenceWords
        else -> "Recognition stays private on your phone"
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Color.White,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(56.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(style.accent)
            )
            Spacer(Modifier.width(14.dp))
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(style.surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    style.icon,
                    contentDescription = null,
                    tint = style.ink,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = statusTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun FaceEnrollmentCard(
    enrollName: String,
    onNameChange: (String) -> Unit,
    samplesCollected: Int,
    minSamples: Int,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = (samplesCollected.toFloat() / minSamples.toFloat()).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(280),
        label = "enrollProgress"
    )
    val ready = samplesCollected >= minSamples

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Color.White,
        shadowElevation = 3.dp
    ) {
        Column(Modifier.padding(16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PairingAvatarInitials(
                name = enrollName.ifBlank { "?" },
                surfaceColor = FaceInsightsTheme.UnknownSurface,
                inkColor = FaceInsightsTheme.UnknownInk,
                sizeDp = 44
            )
            Column(Modifier.weight(1f)) {
                Text(
                    "Enroll new face",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (ready) "Ready to save — enter a name"
                    else "Hold still, look at the camera",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Capture quality",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "$samplesCollected / $minSamples",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (ready) FaceInsightsTheme.KnownInk else MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(8.dp)),
            color = if (ready) FaceInsightsTheme.KnownInk else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        )
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = enrollName,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Name this person") },
            placeholder = { Text("e.g. Saad") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp)
        )
        Spacer(Modifier.height(12.dp))
        PrimaryActionButton(
            text = if (ready) "Save face" else "Collecting samples…",
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            enabled = enrollName.isNotBlank() && ready
        )
        }
    }
}

@Composable
fun FaceMoodSectionHeader(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(FaceInsightsTheme.MoodSurface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Mood,
                contentDescription = null,
                tint = FaceInsightsTheme.MoodInk,
                modifier = Modifier.size(20.dp)
            )
        }
        Column {
            Text(
                "Photo & mood",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Groq analyzes expression — not used for identity",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun FaceOverlayFrame(
    bounds: FaceBoundsNorm?,
    label: String,
    style: FaceStatusStyle,
    pulseIdentifying: Boolean,
    modifier: Modifier = Modifier
) {
    val pulse = rememberInfiniteTransition(label = "facePulse")
    val alpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    BoxWithConstraints(modifier) {
        val fullW = maxWidth
        val fullH = maxHeight
        Canvas(Modifier.fillMaxSize()) {
            val b = bounds ?: return@Canvas
            val wPx = size.width
            val hPx = size.height
            val left = b.left * wPx
            val top = b.top * hPx
            val rw = (b.right - b.left) * wPx
            val rh = (b.bottom - b.top) * hPx
            val strokeW = if (pulseIdentifying) 4f * alpha else 3.5f
            drawRoundRect(
                color = style.accent.copy(alpha = if (pulseIdentifying) 0.5f + 0.25f * alpha else 0.9f),
                topLeft = Offset(left, top),
                size = Size(rw.coerceAtLeast(8f), rh.coerceAtLeast(8f)),
                cornerRadius = CornerRadius(18f, 18f),
                style = Stroke(width = strokeW)
            )
        }
        if (bounds != null) {
            val pillX = fullW * bounds.left
            val pillY = (fullH * bounds.bottom + 8.dp).coerceAtMost(fullH - 44.dp)
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = pillX, y = pillY),
                shape = RoundedCornerShape(20.dp),
                color = style.surface.copy(alpha = 0.96f),
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(style.accent)
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = style.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun FaceSavedPersonCard(
    face: FaceProfileItem,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PairingAvatarInitials(
                name = face.name,
                surfaceColor = FaceInsightsTheme.KnownSurface,
                inkColor = FaceInsightsTheme.KnownInk,
                sizeDp = 52
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = face.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "On-device template · ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(face.updatedAt))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.DeleteOutline,
                    contentDescription = "Remove ${face.name}",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
fun FaceEmptySavedList(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(FaceInsightsTheme.ScanSurface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = FaceInsightsTheme.ScanInk
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "No faces saved yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Use the live camera to enroll someone new.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun FaceCameraLoadingOverlay(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.78f))
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(strokeWidth = 3.dp)
        Spacer(Modifier.height(14.dp))
        Text(
            "Starting camera…",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Loading MobileFaceNet on device",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

data class FaceMoodVisual(
    val shortLabel: String,
    val title: String,
    val subtitle: String,
    val emoji: String,
    val surface: Color
)

fun faceMoodVisual(hint: String): FaceMoodVisual = when {
    hint.contains("Positive", ignoreCase = true) || hint.contains("Good", ignoreCase = true) ->
        FaceMoodVisual("Good", "Positive mood", "Expression looks upbeat", "😊", MindcareColors.CardMint)
    hint.contains("Neutral", ignoreCase = true) || hint.contains("Calm", ignoreCase = true) ->
        FaceMoodVisual("Calm", "Calm & steady", "Relaxed, neutral expression", "😌", MindcareColors.CardLavender)
    hint.contains("No photo", ignoreCase = true) || hint.contains("No face", ignoreCase = true) ->
        FaceMoodVisual("—", "Awaiting photo", "Pick a photo or use the camera", "📷", MindcareColors.CardPeach.copy(alpha = 0.6f))
    else ->
        FaceMoodVisual("OK", "Balanced mood", hint.ifBlank { "Ready to analyze" }, "🙂", MindcareColors.CardPink)
}

@Composable
fun FaceMindcareWelcomeCard(
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        shadowElevation = 3.dp
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(MindcareColors.CardLavender, MindcareColors.CardMint)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Face, contentDescription = null, tint = MindcareColors.Ink, modifier = Modifier.size(30.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "Face insights",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MindcareColors.Ink
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MindcareColors.Ink.copy(alpha = 0.65f)
                )
            }
        }
    }
}

@Composable
fun FaceMindcareStatPill(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = accent.copy(alpha = 0.42f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MindcareColors.Ink
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MindcareColors.Ink.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun FaceMindcareActionChip(
    label: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Surface(
        modifier = modifier
            .width(108.dp)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = tint.copy(alpha = if (enabled) 0.5f else 0.25f)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MindcareColors.Ink.copy(alpha = 0.75f), modifier = Modifier.size(24.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MindcareColors.Ink,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun FaceMoodHeroCard(
    moodHint: String,
    isAnalyzing: Boolean,
    modifier: Modifier = Modifier
) {
    val visual = faceMoodVisual(moodHint)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MindcareSectionTitle("Mood & expression")
                if (isAnalyzing) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(visual.surface),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = visual.emoji,
                        transitionSpec = { fadeIn(tween(280)) togetherWith fadeOut(tween(200)) },
                        label = "moodEmoji"
                    ) { emoji ->
                        Text(
                            emoji,
                            fontSize = 40.sp,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    AnimatedContent(
                        targetState = visual.title,
                        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(220)) },
                        label = "moodTitle"
                    ) { title ->
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MindcareColors.Ink
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        visual.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MindcareColors.Ink.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = visual.surface.copy(alpha = 0.85f)
                    ) {
                        Text(
                            visual.shortLabel,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MindcareColors.Ink
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FaceMindcareAnalysisPanel(
    summary: String?,
    error: String?,
    modifier: Modifier = Modifier
) {
    if (summary.isNullOrBlank() && error.isNullOrBlank()) return
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MindcareColors.CardMint.copy(alpha = 0.45f)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "AI insight",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MindcareColors.Ink
            )
            Spacer(Modifier.height(6.dp))
            if (!summary.isNullOrBlank()) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MindcareColors.Ink.copy(alpha = 0.85f),
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                )
            }
            if (!error.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
