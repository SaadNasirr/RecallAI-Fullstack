package com.example.recallai.recallassistant

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.recallai.ui.components.LoadingPhaseCaption
import com.example.recallai.ui.components.RecallAnswerSkeleton
import com.example.recallai.ui.components.RecallHistoryCard
import com.example.recallai.ui.components.RecallHistoryCardVariant
import com.example.recallai.ui.components.RecallTopBar
import com.example.recallai.ui.components.formatMemoryInstantMs
import com.example.recallai.ui.dashboard.MindcareColors
import com.example.recallai.ui.dashboard.MindcareGradientBackground
import com.example.recallai.ui.dashboard.MindcarePrimaryPillButton
import com.example.recallai.ui.dashboard.MindcareSectionTitle
import com.example.recallai.voice.RemoteSpeechPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class ParsedSource(val type: String, val body: String)

private fun parseSource(raw: String): ParsedSource {
    val match = Regex("^\\[([^\\]]+)]\\s*(.*)$").find(raw.trim())
    return if (match != null) {
        ParsedSource(
            type = match.groupValues[1].replaceFirstChar { it.uppercase() },
            body = match.groupValues[2].ifBlank { raw }
        )
    } else {
        ParsedSource(type = "Memory", body = raw)
    }
}

private fun sourceTint(type: String): Color = when (type.lowercase()) {
    "object" -> MindcareColors.CardPeach
    "note" -> MindcareColors.CardLavender
    "event" -> MindcareColors.CardMint
    else -> Color(0xFFE8E4FE)
}

@Composable
fun LivelyRecallAssistantScreen(
    onBack: () -> Unit,
    onNavigateMemories: () -> Unit = {},
    viewModel: RecallAssistantViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isReadingAloud by RemoteSpeechPlayer.isReadingAloud.collectAsState()
    var contentVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.applyPendingMemoryOpen()
        delay(60)
        contentVisible = true
    }

    MindcareGradientBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                RecallTopBar(title = "Recall", onBack = onBack)
            }
        ) { padding ->
            AnimatedVisibility(
                visible = contentVisible,
                enter = fadeIn(tween(450)) + slideInVertically(
                    initialOffsetY = { it / 6 },
                    animationSpec = tween(500)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color.White,
                        shadowElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = state.query,
                            onValueChange = viewModel::onQueryChange,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("What do you want to remember?") },
                            leadingIcon = {
                                Icon(Icons.Filled.Search, contentDescription = null, tint = MindcareColors.Ink.copy(0.5f))
                            },
                            minLines = 2,
                            maxLines = 4,
                            shape = RoundedCornerShape(50)
                        )
                    }

                    MindcarePrimaryPillButton(
                        text = if (state.isLoading) "Searching…" else "Find",
                        onClick = { viewModel.submit() },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        color = Color.White,
                        shadowElevation = 4.dp
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            MindcareSectionTitle("Answer")
                            Spacer(Modifier.height(10.dp))

                            when {
                                state.isLoading -> {
                                    val phaseText = when (state.loadingPhase) {
                                        RecallLoadingPhase.GENERATING_ANSWER -> "Generating answer…"
                                        else -> "Searching your memories…"
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 140.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            LoadingPhaseCaption(phaseText)
                                            RecallAnswerSkeleton()
                                        }
                                    }
                                }

                                state.responseText != null -> {
                                    Text(
                                        text = state.responseText.orEmpty(),
                                        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
                                        color = MindcareColors.Ink,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    state.error?.let {
                                        Spacer(Modifier.height(8.dp))
                                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                    }
                                    state.info?.let {
                                        Spacer(Modifier.height(6.dp))
                                        Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                                    }
                                    Spacer(Modifier.height(14.dp))
                                    MindcarePrimaryPillButton(
                                        text = "Open memory bank",
                                        onClick = onNavigateMemories,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    if (state.sources.isNotEmpty()) {
                                        Spacer(Modifier.height(16.dp))
                                        Text(
                                            "Sources",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MindcareColors.Ink
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            state.sources.forEachIndexed { index, raw ->
                                                var srcVisible by remember(raw) { mutableStateOf(false) }
                                                LaunchedEffect(raw) {
                                                    delay((index * 70L).coerceAtMost(350L))
                                                    srcVisible = true
                                                }
                                                AnimatedVisibility(
                                                    visible = srcVisible,
                                                    enter = fadeIn(tween(350)) + slideInVertically(
                                                        initialOffsetY = { it / 5 },
                                                        animationSpec = tween(400)
                                                    )
                                                ) {
                                                    LivelySourceCard(
                                                        source = parseSource(raw),
                                                        onCopy = { clipboard.setText(AnnotatedString(raw)) }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(Modifier.height(14.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        LivelyActionChip("Save note", onClick = { viewModel.saveCurrentAnswerAsNote() })
                                        LivelyActionChip("Copy", Icons.Filled.ContentCopy) {
                                            val txt = state.responseText.orEmpty()
                                            if (txt.isNotBlank()) clipboard.setText(AnnotatedString(txt))
                                        }
                                        LivelyActionChip(
                                            label = if (isReadingAloud) "Stop" else "Read aloud",
                                            icon = Icons.Filled.VolumeUp,
                                            onClick = {
                                                if (isReadingAloud) {
                                                    RemoteSpeechPlayer.stop()
                                                } else {
                                                    val txt = state.responseText.orEmpty()
                                                    if (txt.isNotBlank()) {
                                                        scope.launch {
                                                            runCatching { RemoteSpeechPlayer.speak(context, txt) }
                                                        }
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }

                                else -> {
                                    Text(
                                        "Ask a question, then tap Find — your grounded answer appears here.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    state.error?.let {
                                        Spacer(Modifier.height(8.dp))
                                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }

                    state.lastSavedRecall?.let { last ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Filled.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                MindcareSectionTitle("Last recall")
                            }
                            TextButton(onClick = onNavigateMemories) {
                                Text("View all", fontWeight = FontWeight.SemiBold)
                            }
                        }
                        RecallHistoryCard(
                            question = last.question,
                            answer = last.answer,
                            timeLabel = formatMemoryInstantMs(last.createdAt),
                            variant = RecallHistoryCardVariant.Featured,
                            showAudioControls = true,
                            isSpeaking = isReadingAloud,
                            onReadAloud = {
                                scope.launch { runCatching { RemoteSpeechPlayer.speak(context, last.answer) } }
                            },
                            onStopAudio = { RemoteSpeechPlayer.stop() }
                        )
                    }

                    if (state.recentRecalls.isNotEmpty()) {
                        MindcareSectionTitle("Other recent recalls")
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            state.recentRecalls.forEach { item ->
                                RecallHistoryCard(
                                    question = item.question,
                                    answer = item.answer,
                                    timeLabel = formatMemoryInstantMs(item.createdAt),
                                    variant = RecallHistoryCardVariant.CompactList
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(72.dp))
                }
            }
        }
    }
}

@Composable
private fun LivelySourceCard(
    source: ParsedSource,
    onCopy: () -> Unit
) {
    val tint = sourceTint(source.type)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCopy),
        shape = RoundedCornerShape(16.dp),
        color = tint.copy(alpha = 0.35f),
        border = androidx.compose.foundation.BorderStroke(1.dp, tint.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = tint
            ) {
                Text(
                    text = source.type,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MindcareColors.Ink
                )
            }
            Text(
                text = source.body,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MindcareColors.Ink,
                lineHeight = 18.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LivelyActionChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            icon?.let {
                Icon(it, contentDescription = null, modifier = Modifier.height(16.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        }
    }
}
