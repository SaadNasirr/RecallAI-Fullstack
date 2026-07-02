package com.example.recallai.ui.screens

import com.example.recallai.R
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.verticalScroll
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import java.util.Calendar
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.recallai.ui.patient.PatientAssignedCareTasksSection
import com.example.recallai.ui.patient.PatientScheduleSection
import com.example.recallai.ui.components.*
import com.example.recallai.data.AuthManager

/** Frozen copy of patient dashboard before mindcare redesign. Restored when [DashboardLayout.USE_MINDCARE_STYLE] is false. */
@Composable
fun PatientHomeScreenLegacy(
    onBack: () -> Unit = {},
    onNavigateToChat: () -> Unit = {},
    onNavigateToMemories: () -> Unit = {},
    onNavigateToRecallAssistant: () -> Unit = {},
    onNavigateToObjectLocator: () -> Unit = {},
    onNavigateToFaceInsights: () -> Unit = {},
    onNavigateToGeofencing: () -> Unit = {},
    onNavigateToMedication: () -> Unit = {},
    onNavigateToReminders: () -> Unit = {},
    onNavigateToPeopleBook: () -> Unit = {},
    onNavigateToDemoMode: () -> Unit = {},
    onNavigateToRoutine: () -> Unit = {},
    onNavigateToEmergency: () -> Unit = {},
    onNavigateToPrivacy: () -> Unit = {},
    onNavigateToConnectCaregiver: () -> Unit = {},
    onNavigateToFlashcard: () -> Unit = {},
    onLogout: () -> Unit = {},
    viewModel: PatientHomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val postNotificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                postNotificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
    AppBackdrop() {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(14.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onBack) { Text("Roles") }
                    RecallSettingsMenu(
                        roleTitle = "Patient",
                        onNavigatePrivacy = onNavigateToPrivacy,
                        onLogout = onLogout,
                        onRefreshDashboard = viewModel::refresh
                    )
                }
                val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
                val daytime = when (hour) {
                    in 5..11 -> "morning"
                    in 12..16 -> "afternoon"
                    else -> "evening"
                }
                val medCap = state.medicationLogs.coerceAtMost(6)
                val routCap = state.routineLogs.coerceAtMost(6)
                val careCap = state.careTasksToday.coerceAtMost(4)
                val routineProgress =
                    (((medCap + routCap + careCap).coerceAtMost(10)) * 100 / 10).coerceIn(0, 100)
                val displayName = AuthManager.userName?.takeIf { it.isNotBlank() } ?: "Patient"
                val dailyProgressItems = remember(
                    state.emergencyEvents,
                    state.medicationLogs,
                    state.routineLogs,
                    state.careTasksToday
                ) {
                    listOf(
                        Triple("Emergency", state.emergencyEvents, onNavigateToEmergency),
                        Triple("Medication", state.medicationLogs, onNavigateToMedication),
                        Triple("Routine", state.routineLogs, onNavigateToRoutine),
                        Triple("Care tasks", state.careTasksToday, onNavigateToMemories),
                        Triple("Reminders", -1, onNavigateToReminders)
                    ).sortedWith(
                        compareByDescending<Triple<String, Int, () -> Unit>> { (label, count, _) ->
                            when (label) {
                                "Reminders" -> Int.MIN_VALUE
                                else -> count
                            }
                        }.thenBy { (label, _, _) ->
                            when (label) {
                                "Emergency" -> 0
                                "Medication" -> 1
                                "Routine" -> 2
                                "Care tasks" -> 3
                                else -> 4
                            }
                        }
                    )
                }
                Text(
                    text = "Good $daytime,",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "$displayName.",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Daily progress", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        }
                        StatPill("Done", "$routineProgress%")
                    }
                    Spacer(Modifier.height(8.dp))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 2.dp)
                    ) {
                        items(dailyProgressItems, key = { it.first }) { (label, count, onClick) ->
                            val chipLabel = when (label) {
                                "Reminders" -> "Reminders"
                                else -> "$label ($count)"
                            }
                            AnimatedAssistChip(
                                label = chipLabel,
                                onClick = onClick,
                                modifier = Modifier.wrapContentWidth()
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                PatientScheduleSection(
                    state = state,
                    onAddAlarm = viewModel::addAlarm,
                    onDeleteAlarm = viewModel::deleteAlarm,
                    onAddReminder = viewModel::addReminder,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                PatientAssignedCareTasksSection(
                    tasks = state.assignedCareTasks,
                    onDone = viewModel::markAssignedCareTaskDone,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                PrimaryActionButton(
                    text = "Link caregiver",
                    onClick = onNavigateToConnectCaregiver,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassCard(
                        modifier = Modifier
                            .width(152.dp)
                            .clickable { onNavigateToChat() }
                    ) {
                        Text(
                            "Talk to AI",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "The AI is ready to remember.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    GlassCard(
                        modifier = Modifier
                            .width(152.dp)
                            .clickable { onNavigateToGeofencing() }
                    ) {
                        Text(
                            "Where am I?",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "Find your way home safely.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    GlassCard(
                        modifier = Modifier
                            .width(152.dp)
                            .clickable { onNavigateToFlashcard() }
                    ) {
                        Text(
                            "Memory Games",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "Flip cards, train recall.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    GlassCard(
                        modifier = Modifier
                            .width(152.dp)
                            .clickable { onNavigateToEmergency() }
                    ) {
                        Text(
                            "Emergency",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "Press in urgent situations.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                SectionTitle("AI tools")
                Spacer(Modifier.height(10.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ModelGoldMedalMiniCard(
                            title = "Therapist Chat",
                            statLine = "${state.chatCount + state.voiceCount} voice & chat memories",
                            icon = Icons.AutoMirrored.Filled.Chat,
                            illustrationRes = R.drawable.img_tool_chatbot,
                            onOpen = onNavigateToChat,
                            modifier = Modifier.weight(1f)
                        )
                        ModelGoldMedalMiniCard(
                            title = "Object Intelligence",
                            statLine = "${state.objectDetections} vision detections",
                            icon = Icons.Filled.Visibility,
                            illustrationRes = R.drawable.img_tool_object,
                            onOpen = onNavigateToObjectLocator,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ModelGoldMedalMiniCard(
                            title = "Face Insights",
                            statLine = "${state.faceInsights} face analyses",
                            icon = Icons.Filled.Face,
                            illustrationRes = R.drawable.img_tool_face,
                            onOpen = onNavigateToFaceInsights,
                            modifier = Modifier.weight(1f)
                        )
                        ModelGoldMedalMiniCard(
                            title = "Semantic Recall",
                            statLine = "${state.totalMemories} memories",
                            icon = Icons.Filled.Psychology,
                            illustrationRes = R.drawable.img_tool_recall,
                            onOpen = onNavigateToRecallAssistant,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                SectionTitle("Care & Health Tools")
                Spacer(Modifier.height(10.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ModelGoldMedalMiniCard(
                            title = "People Directory",
                            statLine = "Save faces & contacts",
                            icon = Icons.Filled.Face,
                            illustrationRes = R.drawable.img_tool_face,
                            onOpen = onNavigateToPeopleBook,
                            modifier = Modifier.weight(1f)
                        )
                        ModelGoldMedalMiniCard(
                            title = "Medication",
                            statLine = "Track doses & alerts",
                            icon = Icons.Filled.Schedule,
                            illustrationRes = R.drawable.img_tool_medication,
                            onOpen = onNavigateToMedication,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ModelGoldMedalMiniCard(
                            title = "Smart Routine",
                            statLine = "Daily habits & streaks",
                            icon = Icons.Filled.Schedule,
                            illustrationRes = R.drawable.img_tool_routine,
                            onOpen = onNavigateToRoutine,
                            modifier = Modifier.weight(1f)
                        )
                        ModelGoldMedalMiniCard(
                            title = "Memory Games",
                            statLine = "Flashcards & recall training",
                            icon = Icons.Filled.Psychology,
                            illustrationRes = R.drawable.img_tool_recall,
                            onOpen = onNavigateToFlashcard,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionTitle("Memories")
                    SecondaryActionButton("All", onClick = onNavigateToMemories)
                }
                Spacer(Modifier.height(8.dp))
                val recent = state.recent.take(3)
                if (recent.isEmpty()) {
                    Text("No memories yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    recent.forEachIndexed { idx, item ->
                        MemoryMedalTimelineCard(
                            memoryId = item.id,
                            rankLabel = "#${idx + 1}",
                            typeLabel = memoryTypeChipLabel(item.type),
                            title = item.title ?: "Memory",
                            preview = item.text,
                            createdAt = item.createdAt,
                            accentColor = memoryAccentForType(item.type),
                            onOpenTimeline = onNavigateToMemories,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }
    }
}
