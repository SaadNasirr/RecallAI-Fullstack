package com.example.recallai.medication

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.recallai.R
import com.example.recallai.data.MedicationItem
import com.example.recallai.ui.components.AnimatedAssistChip
import com.example.recallai.ui.components.AppBackdrop
import com.example.recallai.ui.components.GlassCard
import com.example.recallai.ui.components.HeroHeaderCard
import com.example.recallai.ui.components.PrimaryActionButton
import com.example.recallai.ui.components.QuickSwitchRow
import com.example.recallai.ui.components.RecallTopBar
import com.example.recallai.ui.components.SectionTitle
import com.example.recallai.ui.components.StatPill
import com.example.recallai.ui.components.TonalActionButton

private val statusColor = mapOf(
    "TAKEN" to Color(0xFF43A047),
    "MISSED" to Color(0xFFE53935),
    "SNOOZED" to Color(0xFFFB8C00),
    "SKIPPED" to Color(0xFFFF7043),
    "PENDING" to Color(0xFF9E9E9E)
)

private fun statusColor(status: String): Color =
    statusColor[status] ?: Color(0xFF9E9E9E)

private fun periodFor(timeLabel: String): String {
    val lower = timeLabel.lowercase()
    val hour = runCatching {
        val t = lower.replace("am", "").replace("pm", "").trim()
        var h = t.split(":")[0].trim().toInt()
        if (lower.contains("pm") && h != 12) h += 12
        if (lower.contains("am") && h == 12) h = 0
        h
    }.getOrElse { -1 }
    return when {
        hour in 5..11 -> "Morning"
        hour in 12..16 -> "Afternoon"
        hour in 17..20 -> "Evening"
        else -> "Night"
    }
}

@Composable
fun MedicationSchedulerScreen(
    onBack: () -> Unit,
    onNavigateHome: () -> Unit = {},
    onNavigateChat: () -> Unit = {},
    onNavigateFace: () -> Unit = {},
    onNavigateMemories: () -> Unit = {},
    onNavigateRecall: () -> Unit = {},
    viewModel: MedicationSchedulerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    AppBackdrop {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = { RecallTopBar(title = "Medication Tracker", onBack = onBack) }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 14.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Spacer(Modifier.height(4.dp))

                HeroHeaderCard(
                    title = "Medication Tracker",
                    subtitle = "Schedule doses, track adherence, and stay on top of your health",
                    illustrationRes = R.drawable.img_tool_medication
                )

                // Stats row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatPill("Total", state.medications.size.toString())
                    StatPill("Taken", state.takenCount.toString())
                    StatPill("Pending", state.pendingCount.toString())
                    StatPill("Missed", state.missedCount.toString())
                }

                // Adherence progress bar
                if (state.medications.isNotEmpty()) {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Today's Adherence",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "${state.adherencePct}%",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    state.adherencePct >= 80 -> statusColor("TAKEN")
                                    state.adherencePct >= 50 -> statusColor("SNOOZED")
                                    else -> statusColor("MISSED")
                                }
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { state.adherencePct / 100f },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = when {
                                state.adherencePct >= 80 -> statusColor("TAKEN")
                                state.adherencePct >= 50 -> statusColor("SNOOZED")
                                else -> statusColor("MISSED")
                            },
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            strokeCap = StrokeCap.Round
                        )
                        if (state.escalationsToday > 0) {
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.ErrorOutline,
                                    null,
                                    tint = statusColor("MISSED"),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "${state.escalationsToday} escalation(s) sent to caregiver",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = statusColor("MISSED")
                                )
                            }
                        }
                    }
                }

                QuickSwitchRow(
                    onHome = onNavigateHome,
                    onChat = onNavigateChat,
                    onFace = onNavigateFace,
                    onMemories = onNavigateMemories,
                    onRecall = onNavigateRecall
                )

                // Add medication form
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    SectionTitle("Add Medication")
                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = state.nameInput,
                        onValueChange = viewModel::onNameChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Medication Name *") },
                        leadingIcon = { Icon(Icons.Default.MedicalServices, null) },
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.timeInput,
                        onValueChange = viewModel::onTimeChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Dose Time (e.g. 8:00 AM)  *") },
                        leadingIcon = { Icon(Icons.Default.Schedule, null) },
                        singleLine = true
                    )

                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("6:00 AM", "8:00 AM", "12:00 PM", "2:00 PM", "6:00 PM", "9:00 PM", "10:00 PM").forEach { t ->
                            SuggestionChip(
                                onClick = { viewModel.onTimeChange(t) },
                                label = { Text(t, fontSize = 12.sp) }
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.noteInput,
                        onValueChange = viewModel::onNoteChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Instructions / Notes") },
                        leadingIcon = { Icon(Icons.Default.Notes, null) },
                        minLines = 2,
                        maxLines = 3
                    )

                    if (!state.info.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = state.info ?: "",
                            color = when {
                                state.info?.contains("removed", ignoreCase = true) == true ||
                                state.info?.contains("missed", ignoreCase = true) == true ->
                                    statusColor("MISSED")
                                state.info?.contains("handled", ignoreCase = true) == true ->
                                    statusColor("TAKEN")
                                else -> MaterialTheme.colorScheme.primary
                            },
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PrimaryActionButton(
                            text = "Add Medication",
                            onClick = {
                                val item = viewModel.addMedication() ?: return@PrimaryActionButton
                                if (Build.VERSION.SDK_INT >= 33) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                MedicationEscalationScheduler.scheduleDailyDose(
                                    context = context,
                                    medicationId = item.id,
                                    name = item.name,
                                    timeLabel = item.timeLabel
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TonalActionButton(
                            text = "Check Missed",
                            onClick = viewModel::runAdherenceCheck
                        )
                    }
                }

                // Grouped medication list
                if (state.medications.isNotEmpty()) {
                    val grouped = state.medications.groupBy { periodFor(it.timeLabel) }
                    val order = listOf("Morning", "Afternoon", "Evening", "Night")
                    order.forEach { period ->
                        val meds = grouped[period] ?: return@forEach
                        SectionTitle(period)
                        meds.forEach { med ->
                            MedicationCard(
                                med = med,
                                onMarkTaken = { viewModel.markTaken(med.id) },
                                onSnooze = { viewModel.snoozeDose(med.id) },
                                onSkip = { viewModel.skipDose(med.id) },
                                onRemove = { viewModel.removeMedication(med.id) }
                            )
                        }
                    }
                    // Meds without recognizable time
                    val ungrouped = state.medications.filter { periodFor(it.timeLabel) !in order }
                    if (ungrouped.isNotEmpty()) {
                        SectionTitle("Other")
                        ungrouped.forEach { med ->
                            MedicationCard(
                                med = med,
                                onMarkTaken = { viewModel.markTaken(med.id) },
                                onSnooze = { viewModel.snoozeDose(med.id) },
                                onSkip = { viewModel.skipDose(med.id) },
                                onRemove = { viewModel.removeMedication(med.id) }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun MedicationCard(
    med: MedicationItem,
    onMarkTaken: () -> Unit,
    onSnooze: () -> Unit,
    onSkip: () -> Unit,
    onRemove: () -> Unit
) {
    val color = statusColor(med.adherenceStatus)

    GlassCard(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            // Status dot
            Box(
                modifier = Modifier
                    .padding(top = 3.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = med.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    // Status badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(color.copy(alpha = 0.18f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = med.adherenceStatus,
                            style = MaterialTheme.typography.labelSmall,
                            color = color,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AccessTime,
                        null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        med.timeLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (med.snoozeCount > 0) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Snoozed ${med.snoozeCount}×",
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor("SNOOZED")
                        )
                    }
                }

                if (med.notes.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        med.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (med.skippedToday && med.skipReason.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Skip reason: ${med.skipReason}",
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor("SKIPPED")
                    )
                }

                Spacer(Modifier.height(10.dp))

                AnimatedVisibility(
                    visible = !med.takenToday && !med.skippedToday,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AssistChip(
                            onClick = onMarkTaken,
                            label = { Text("Mark Taken", fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(14.dp))
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = statusColor("TAKEN").copy(alpha = 0.12f),
                                labelColor = statusColor("TAKEN"),
                                leadingIconContentColor = statusColor("TAKEN")
                            )
                        )
                        AssistChip(
                            onClick = onSnooze,
                            label = { Text("Snooze", fontSize = 12.sp) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = statusColor("SNOOZED").copy(alpha = 0.12f),
                                labelColor = statusColor("SNOOZED")
                            )
                        )
                        AssistChip(
                            onClick = onSkip,
                            label = { Text("Skip", fontSize = 12.sp) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = statusColor("SKIPPED").copy(alpha = 0.12f),
                                labelColor = statusColor("SKIPPED")
                            )
                        )
                    }
                }

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AssistChip(
                        onClick = onRemove,
                        label = { Text("Remove", fontSize = 12.sp) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                            labelColor = MaterialTheme.colorScheme.error
                        )
                    )
                }
            }
        }
    }
}
