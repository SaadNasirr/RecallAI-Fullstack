package com.example.recallai.flashcard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recallai.ui.dashboard.MindcareColors
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.recallai.ui.components.AppBackdrop
import com.example.recallai.ui.components.GlassCard
import com.example.recallai.ui.components.RecallTopBar
import com.example.recallai.ui.components.StatPill

private val correctColor = Color(0xFF43A047)
private val incorrectColor = Color(0xFFE53935)
private val hintColor = Color(0xFFFB8C00)
private val matchColor = Color(0xFF00897B)

@Composable
fun FlashcardScreen(
    onBack: () -> Unit,
    viewModel: FlashcardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    AppBackdrop {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = { RecallTopBar(title = "Memory Flashcards", onBack = onBack) }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when {
                    state.isLoading -> LoadingState()
                    state.cards.isEmpty() -> EmptyState(onRefresh = viewModel::loadCards)
                    state.sessionComplete -> SessionCompleteState(
                        state = state,
                        onRestart = viewModel::restartSession
                    )
                    else -> ActiveSessionState(state = state, viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(
                "Loading your memories…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyState(onRefresh: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.AutoAwesome,
                null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "No memories to practice yet",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Add people, log medications, or chat with the AI — then come back to train recall.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Try Again")
            }
        }
    }
}

@Composable
private fun SessionCompleteState(state: FlashcardUiState, onRestart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(
                    when {
                        state.score >= 80 -> correctColor.copy(alpha = 0.15f)
                        state.score >= 50 -> hintColor.copy(alpha = 0.15f)
                        else -> incorrectColor.copy(alpha = 0.15f)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.EmojiEvents,
                null,
                modifier = Modifier.size(52.dp),
                tint = when {
                    state.score >= 80 -> correctColor
                    state.score >= 50 -> hintColor
                    else -> incorrectColor
                }
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = when {
                state.score >= 80 -> "Excellent Recall!"
                state.score >= 60 -> "Good Job!"
                state.score >= 40 -> "Keep Practicing"
                else -> "Don't Give Up!"
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "You scored ${state.score}% on ${state.totalCards} cards",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ScoreStat("Correct", state.correctCount.toString(), correctColor)
                ScoreStat("Incorrect", state.incorrectCount.toString(), incorrectColor)
                ScoreStat("Score", "${state.score}%", MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onRestart,
            modifier = Modifier.fillMaxWidth(0.7f)
        ) {
            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Practice Again")
        }
    }
}

@Composable
private fun ScoreStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ActiveSessionState(
    state: FlashcardUiState,
    viewModel: FlashcardViewModel
) {
    val card = state.currentCard ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Progress header
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Card ${state.currentIndex + 1} of ${state.totalCards}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StatPill("Correct", state.correctCount.toString())
                    StatPill("Wrong", state.incorrectCount.toString())
                }
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round
            )
        }

        MemoryFlipCard(
            isFlipped = state.isFlipped,
            question = card.question,
            answer = card.answer,
            category = card.category
        )

        // Before flip: hint + answer input + flip button
        AnimatedVisibility(
            visible = !state.isFlipped,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Hint
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(hintColor.copy(alpha = 0.1f))
                        .padding(10.dp)
                ) {
                    Icon(
                        Icons.Default.Lightbulb,
                        null,
                        tint = hintColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        card.hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = hintColor
                    )
                }

                // User answer input
                OutlinedTextField(
                    value = state.userAnswer,
                    onValueChange = viewModel::onUserAnswerChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Type your answer before revealing…") },
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(12.dp),
                    isError = state.showAnswerRequired
                )

                // Warning shown when user taps Reveal without typing
                AnimatedVisibility(visible = state.showAnswerRequired) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(incorrectColor.copy(alpha = 0.08f))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            null,
                            tint = incorrectColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Write something first — even a guess counts!",
                            style = MaterialTheme.typography.labelMedium,
                            color = incorrectColor
                        )
                    }
                }

                FilledTonalButton(
                    onClick = viewModel::flipCard,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FlipCameraAndroid, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Flip card")
                }
            }
        }

        // After flip: user answer + match hint + self-assess buttons (answer is on card back)
        AnimatedVisibility(
            visible = state.isFlipped,
            enter = fadeIn(tween(300)) + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // User's answer (flip requires non-blank input)
                val matched = state.answerMatchesKeyTerms()
                val answerBorderColor = when {
                    card.keyTerms.isNotEmpty() && matched -> matchColor
                    card.keyTerms.isNotEmpty() -> hintColor
                    else -> MaterialTheme.colorScheme.outline
                }

                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "YOUR ANSWER",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        if (card.keyTerms.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(answerBorderColor.copy(alpha = 0.12f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    if (matched) Icons.Default.Check else Icons.Default.Close,
                                    null,
                                    tint = answerBorderColor,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (matched) "Key terms matched" else "No key terms",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = answerBorderColor,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        state.userAnswer,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 22.sp
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Text(
                    "How well did you remember it?",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = viewModel::answerIncorrect,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = incorrectColor)
                    ) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Didn't Know")
                    }
                    Button(
                        onClick = viewModel::answerCorrect,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = correctColor)
                    ) {
                        Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Got It!")
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun MemoryFlipCard(
    isFlipped: Boolean,
    question: String,
    answer: String,
    category: String,
    modifier: Modifier = Modifier
) {
    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
        label = "memoryFlip"
    )
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp)
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 14f * density.density
            }
    ) {
        FlipCardFace(
            label = "Question",
            body = question,
            category = category,
            background = MindcareColors.CardLavender,
            labelColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    alpha = if (rotation <= 90f) 1f else 0f
                }
        )
        FlipCardFace(
            label = "Answer",
            body = answer,
            category = category,
            background = MindcareColors.CardMint,
            labelColor = correctColor,
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    rotationY = 180f
                    alpha = if (rotation > 90f) 1f else 0f
                }
        )
    }
}

@Composable
private fun FlipCardFace(
    label: String,
    body: String,
    category: String,
    background: Color,
    labelColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(background)
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.65f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        category,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MindcareColors.Ink
                    )
                }
            }
            Text(
                body,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = MindcareColors.Ink,
                lineHeight = 26.sp,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp)
            )
            if (label.equals("Question", ignoreCase = true)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.FlipCameraAndroid,
                        contentDescription = null,
                        tint = MindcareColors.Ink.copy(alpha = 0.45f),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Type your guess, then flip",
                        style = MaterialTheme.typography.labelSmall,
                        color = MindcareColors.Ink.copy(alpha = 0.45f)
                    )
                }
            } else {
                Text(
                    "How close were you?",
                    style = MaterialTheme.typography.labelSmall,
                    color = MindcareColors.Ink.copy(alpha = 0.5f)
                )
            }
        }
    }
}
