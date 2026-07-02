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

/** Frozen copy of caregiver dashboard before mindcare redesign. Restored when [DashboardLayout.USE_MINDCARE_STYLE] is false. */
@Composable
fun CaregiverHomeScreenLegacy(
    onBack: () -> Unit = {},
    onNavigateToAlertRules: () -> Unit = {},
    onNavigateToTimeline: () -> Unit = {},
    onNavigateToZones: () -> Unit = {},
    onNavigateToPatients: () -> Unit = {},
    onNavigateToCareCoordination: () -> Unit = {},
    onNavigateToAlertCenter: () -> Unit = {},
    onNavigateToPrivacy: () -> Unit = {},
    onNavigateToAddPatient: () -> Unit = {},
    onLogout: () -> Unit = {},
    viewModel: CaregiverHomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    AppBackdrop() {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                RecallTopBar(
                    title = "Care dashboard",
                    onBack = onBack,
                    actions = {
                        RecallSettingsMenu(
                            roleTitle = "Caregiver",
                            onNavigatePrivacy = onNavigateToPrivacy,
                            onLogout = onLogout,
                            onRefreshDashboard = viewModel::refresh
                        )
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(14.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HeroHeaderCard(
                    title = "Care center",
                    subtitle = "",
                    illustrationRes = R.drawable.ill_patient_caregiver
                )

                state.latestMoodLine?.let { mood ->
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text("Patient mood", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(mood, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassCard(modifier = Modifier.weight(1f)) {
                        Text("Total Memories", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(state.totalMemories.toString(), style = MaterialTheme.typography.headlineSmall)
                    }
                    GlassCard(modifier = Modifier.weight(1f)) {
                        Text("Active Alerts", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            state.riskAlerts.toString(),
                            style = MaterialTheme.typography.headlineSmall,
                            color = if (state.riskAlerts > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatPill(label = "Mode", value = "Caregiver")
                    StatPill(label = "Patients", value = "1")
                    StatPill(label = "Status", value = if (state.riskAlerts > 0) "Attention" else "Stable")
                    StatPill(label = "Meds", value = state.medicationLogs.toString())
                    StatPill(label = "Escalations", value = state.medicationAlerts.toString())
                }

                PrimaryActionButton(
                    text = "Add patient",
                    onClick = onNavigateToAddPatient,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))

                SectionTitle("Tools")
                Spacer(Modifier.height(6.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ModelGoldMedalMiniCard(
                            title = "Alert Center",
                            statLine = "${state.riskAlerts} risk signals",
                            icon = Icons.Filled.Warning,
                            illustrationRes = R.drawable.img_tool_emergency,
                            onOpen = onNavigateToAlertCenter,
                            modifier = Modifier.weight(1f)
                        )
                        ModelGoldMedalMiniCard(
                            title = "Watchlist",
                            statLine = "Patients 1",
                            icon = Icons.Filled.Visibility,
                            illustrationRes = R.drawable.img_tool_face,
                            onOpen = onNavigateToPatients,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ModelGoldMedalMiniCard(
                            title = "Zones",
                            statLine = "Geofence safety",
                            icon = Icons.Filled.LocationOn,
                            illustrationRes = R.drawable.img_tool_geofence,
                            onOpen = onNavigateToZones,
                            modifier = Modifier.weight(1f)
                        )
                        ModelGoldMedalMiniCard(
                            title = "Care Tasks",
                            statLine = "${state.pendingCareTasks} open tasks",
                            icon = Icons.Filled.Schedule,
                            illustrationRes = R.drawable.img_tool_caregiver,
                            onOpen = onNavigateToCareCoordination,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SecondaryActionButton(text = "Rules", onClick = onNavigateToAlertRules)
                        SecondaryActionButton(text = "Timeline", onClick = onNavigateToTimeline)
                        SecondaryActionButton(text = "Refresh", onClick = viewModel::refresh)
                    }
                }

                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    SectionTitle("Next step")
                    Spacer(Modifier.height(6.dp))
                    val nextActionText = when {
                        state.riskAlerts > 0 && state.medicationAlerts > 0 ->
                            "Resolve medication escalations in alerts first."
                        state.riskAlerts > 0 ->
                            "Review risk signals on the watchlist."
                        state.pendingCareTasks > 0 ->
                            "Finish open care tasks."
                        else ->
                            "Stable; spot-check the timeline."
                    }
                    Text(nextActionText, style = MaterialTheme.typography.bodyMedium)
                }

                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        SectionTitle("Alerts")
                    }
                    Spacer(Modifier.height(6.dp))
                    if (state.riskAlerts == 0) {
                        Text("No alerts.")
                    } else {
                        Text("${state.riskAlerts} alert signals need your review.")
                        Spacer(Modifier.height(8.dp))
                        state.alertBreakdown.forEachIndexed { index, trigger ->
                            val triggerColor = when (trigger.severity) {
                                CaregiverTriggerSeverity.HIGH -> MaterialTheme.colorScheme.error
                                CaregiverTriggerSeverity.MEDIUM -> MaterialTheme.colorScheme.tertiary
                                CaregiverTriggerSeverity.INFO -> MaterialTheme.colorScheme.primary
                            }
                            val triggerIcon = when (trigger.iconType) {
                                CaregiverTriggerAction.OPEN_RULES -> Icons.Filled.Warning
                                CaregiverTriggerAction.OPEN_TIMELINE -> Icons.Filled.Schedule
                                CaregiverTriggerAction.OPEN_ZONES -> Icons.Filled.LocationOn
                            }
                            var visible by remember(trigger.message) { mutableStateOf(false) }
                            LaunchedEffect(trigger.message) {
                                delay((index * 70L).coerceAtMost(350L))
                                visible = true
                            }
                            AnimatedVisibility(
                                visible = visible,
                                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 3 })
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = triggerIcon,
                                            contentDescription = null,
                                            tint = triggerColor
                                        )
                                        Text(
                                            text = trigger.message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = triggerColor
                                        )
                                    }
                                    val ctaInteraction = remember(trigger.message) { MutableInteractionSource() }
                                    val ctaPressed by ctaInteraction.collectIsPressedAsState()
                                    val ctaScale by animateFloatAsState(
                                        targetValue = if (ctaPressed) 0.96f else 1f,
                                        animationSpec = tween(durationMillis = 120),
                                        label = "alertCtaScale"
                                    )
                                    TextButton(
                                        modifier = Modifier.graphicsLayer {
                                            scaleX = ctaScale
                                            scaleY = ctaScale
                                        },
                                        interactionSource = ctaInteraction,
                                        onClick = {
                                            when (trigger.action) {
                                                CaregiverTriggerAction.OPEN_RULES -> onNavigateToAlertRules()
                                                CaregiverTriggerAction.OPEN_TIMELINE -> onNavigateToTimeline()
                                                CaregiverTriggerAction.OPEN_ZONES -> onNavigateToZones()
                                            }
                                        }
                                    ) {
                                        Text(trigger.actionLabel)
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }

                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    SectionTitle("Insights")
                    Spacer(Modifier.height(6.dp))
                    if (state.careInsights.isEmpty()) {
                        Text("No care insights yet.")
                    } else {
                        state.careInsights.forEach {
                            Text("• $it", style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }

                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    SectionTitle("Trends")
                    Spacer(Modifier.height(6.dp))
                    state.trendInsights.forEach {
                        Text("• $it", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(4.dp))
                    }
                }

                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    SectionTitle("Activity")
                    Spacer(Modifier.height(8.dp))
                    if (state.isLoading) {
                        Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else if (state.recent.isEmpty()) {
                        Text("No recent activity yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        state.recent.forEachIndexed { index, item ->
                            MemoryMedalTimelineCard(
                                memoryId = item.id,
                                rankLabel = "#${index + 1}",
                                typeLabel = memoryTypeChipLabel(item.type),
                                title = item.title ?: item.type.replace("_", " "),
                                preview = item.text,
                                createdAt = item.createdAt,
                                accentColor = memoryAccentForType(item.type),
                                onOpenTimeline = onNavigateToTimeline,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
            }
        }
    }
}
