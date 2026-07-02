@file:Suppress("UNUSED_PARAMETER")

package com.example.recallai.recallassistant

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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.recallai.ui.components.AnimatedAssistChip
import com.example.recallai.ui.components.AppBackdrop
import com.example.recallai.ui.components.GlassCard
import com.example.recallai.ui.components.LoadingPhaseCaption
import com.example.recallai.ui.components.PrimaryActionButton
import com.example.recallai.ui.components.RecallAnswerSkeleton
import com.example.recallai.ui.components.RecallTopBar
import com.example.recallai.ui.components.RecallHistoryCard
import com.example.recallai.ui.components.RecallHistoryCardVariant
import com.example.recallai.ui.components.formatMemoryInstantMs
import com.example.recallai.voice.RemoteSpeechPlayer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
/** Frozen copy before lively Recall UI. Restored when [RecallUiLayout.USE_LIVELY_RECALL_UI] is false. */
@Composable
fun RecallAssistantScreenLegacy(
    onBack: () -> Unit,
    onNavigateHome: () -> Unit = {},
    onNavigateChat: () -> Unit = {},
    onNavigateFace: () -> Unit = {},
    onNavigateMemories: () -> Unit = {},
    onNavigateRecall: () -> Unit = {},
    viewModel: RecallAssistantViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isReadingAloud by RemoteSpeechPlayer.isReadingAloud.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.applyPendingMemoryOpen()
    }

    AppBackdrop() {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                RecallTopBar(
                    title = "Recall",
                    onBack = onBack
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Ask…") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(18.dp)
                )
                Spacer(Modifier.height(12.dp))

                PrimaryActionButton(
                    text = if (state.isLoading) "Wait" else "Find",
                    onClick = { viewModel.submit() },
                    enabled = !state.isLoading,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Text(
                            text = "Answer",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(8.dp))

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
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    ) {
                                        LoadingPhaseCaption(phaseText)
                                        RecallAnswerSkeleton()
                                    }
                                }
                            }

                            state.responseText != null -> {
                                Text(
                                    text = state.responseText.orEmpty(),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (state.error != null) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = state.error.orEmpty(),
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                if (state.info != null) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = state.info.orEmpty(),
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Spacer(Modifier.height(12.dp))
                                PrimaryActionButton(
                                    text = "Open memory bank",
                                    onClick = onNavigateMemories,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                if (state.sources.isNotEmpty()) {
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        text = "Sources",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        state.sources.forEach { src ->
                                            AssistChip(
                                                onClick = { clipboard.setText(AnnotatedString(src)) },
                                                label = { Text(src, maxLines = 2) },
                                                shape = RoundedCornerShape(16.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = "Actions",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    AnimatedAssistChip(
                                        label = "Save note",
                                        onClick = { viewModel.saveCurrentAnswerAsNote() }
                                    )
                                    AnimatedAssistChip(
                                        label = "Copy text",
                                        onClick = {
                                            val txt = state.responseText.orEmpty()
                                            if (txt.isNotBlank()) clipboard.setText(AnnotatedString(txt))
                                        }
                                    )
                                    AnimatedAssistChip(
                                        label = if (isReadingAloud) "Stop audio" else "Read answer",
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
                                        },
                                        enabled = isReadingAloud || !state.responseText.isNullOrBlank()
                                    )
                                }
                            }

                            else -> {
                                Text(
                                    text = "Your grounded answer will appear here after you tap Review Recall.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (state.error != null) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = state.error.orEmpty(),
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }

                state.lastSavedRecall?.let { last ->
                    Spacer(Modifier.height(18.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Last recall",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        TextButton(onClick = onNavigateMemories) {
                            Text(
                                "View all",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
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
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Other recent recalls",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
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
            }
        }
    }
}
