package com.example.recallai.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.recallai.data.AuthManager
import com.example.recallai.ui.components.RecallSettingsMenu
import com.example.recallai.ui.components.memoryAccentForType
import com.example.recallai.ui.components.memoryTypeChipLabel
import com.example.recallai.ui.screens.CaregiverHomeViewModel

private data class CaregiverActivityItem(
    val title: String,
    val icon: ImageVector,
    val color: androidx.compose.ui.graphics.Color,
    val keywords: List<String>,
    val onClick: () -> Unit
)

@Composable
fun MindcareCaregiverHomeScreen(
    onBack: () -> Unit,
    onNavigateToAlertRules: () -> Unit,
    onNavigateToTimeline: () -> Unit,
    onNavigateToZones: () -> Unit,
    onNavigateToPatients: () -> Unit,
    onNavigateToCareCoordination: () -> Unit,
    onNavigateToAlertCenter: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToAddPatient: () -> Unit,
    onLogout: () -> Unit,
    viewModel: CaregiverHomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var period by remember { mutableStateOf("Today") }
    val displayName = AuthManager.userName?.takeIf { it.isNotBlank() } ?: "Caregiver"

    val activities = remember(
        onNavigateToAlertCenter,
        onNavigateToPatients,
        onNavigateToZones,
        onNavigateToCareCoordination,
        onNavigateToAlertRules,
        onNavigateToTimeline,
        onNavigateToAddPatient
    ) {
        listOf(
            CaregiverActivityItem("Alert Center", Icons.Filled.Warning, MindcareColors.CardPink, listOf("alert", "risk"), onNavigateToAlertCenter),
            CaregiverActivityItem("Watchlist", Icons.Filled.Visibility, MindcareColors.CardLavender, listOf("patient", "watch"), onNavigateToPatients),
            CaregiverActivityItem("Zones", Icons.Filled.LocationOn, MindcareColors.CardMint, listOf("geo", "fence"), onNavigateToZones),
            CaregiverActivityItem("Care Tasks", Icons.Filled.Schedule, MindcareColors.CardPeach, listOf("task", "care"), onNavigateToCareCoordination),
            CaregiverActivityItem("Rules", Icons.Filled.AutoAwesome, MindcareColors.CardLavender, listOf("rule"), onNavigateToAlertRules),
            CaregiverActivityItem("Timeline", Icons.Filled.Schedule, MindcareColors.CardMint, listOf("time", "memo"), onNavigateToTimeline)
        )
    }

    val filtered = remember(searchQuery, activities) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) activities
        else activities.filter { it.title.lowercase().contains(q) || it.keywords.any { k -> k.contains(q) } }
    }

    val nextActionText = when {
        state.careAlertsUnread > 0 ->
            "${state.careAlertsUnread} unread alert(s) — open Alert Center."
        state.riskAlerts > 0 && state.medicationAlerts > 0 ->
            "Resolve medication escalations in alerts first."
        state.riskAlerts > 0 -> "Review risk signals on the watchlist."
        state.pendingCareTasks > 0 -> "Finish open care tasks."
        else -> "Stable; spot-check the timeline."
    }

    MindcareGradientBackground {
        Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                MindcareProfileHeader(
                    displayName = displayName,
                    subtitle = "Care dashboard overview",
                    onProfileClick = onBack,
                    trailing = {
                        androidx.compose.material3.Surface(
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = androidx.compose.ui.graphics.Color.White,
                            shadowElevation = 2.dp,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MindcareColors.Ink.copy(alpha = 0.08f)
                            )
                        ) {
                            RecallSettingsMenu(
                                roleTitle = "Caregiver",
                                onNavigatePrivacy = onNavigateToPrivacy,
                                onLogout = onLogout,
                                onRefreshDashboard = viewModel::refresh
                            )
                        }
                    }
                )
                MindcareFilterPills(
                    options = listOf("Today", "Next week", "Next month"),
                    selected = period,
                    onSelected = { period = it }
                )
                MindcareSearchField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "Search care tools"
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MindcareSimpleCard(modifier = Modifier.weight(1f)) {
                        Text("Memories", style = MaterialTheme.typography.labelSmall)
                        Text(
                            state.totalMemories.toString(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                    }
                    MindcareSimpleCard(modifier = Modifier.weight(1f)) {
                        Text("Active alerts", style = MaterialTheme.typography.labelSmall)
                        Text(
                            state.careAlertsUnread.toString(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = if (state.careAlertsUnread > 0) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MindcareColors.Ink
                            }
                        )
                    }
                }
                state.latestMoodLine?.let { mood ->
                    MindcareSectionTitle("Patient mood")
                    MindcareSimpleCard {
                        Text(mood, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                MindcareSectionTitle("Activities")
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(filtered, key = { it.title }) { item ->
                        MindcareActivityCard(
                            title = item.title,
                            icon = item.icon,
                            background = item.color,
                            onClick = item.onClick
                        )
                    }
                }
                MindcarePrimaryPillButton(text = "Add patient", onClick = onNavigateToAddPatient)
                MindcareSimpleCard {
                    MindcareSectionTitle("Next step")
                    Spacer(Modifier.height(6.dp))
                    Text(nextActionText, style = MaterialTheme.typography.bodyMedium)
                }
                MindcareSimpleCard {
                    MindcareSectionTitle("Alerts")
                    Spacer(Modifier.height(6.dp))
                    if (state.recentCareAlerts.isEmpty()) {
                        Text("No alerts.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        if (state.careAlertsUnread > 0) {
                            Text(
                                "${state.careAlertsUnread} unread · tap Alert Center for full list",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        state.recentCareAlerts.take(3).forEach { alert ->
                            val title = alert.title?.trim().takeIf { !it.isNullOrBlank() } ?: "Alert"
                            val preview = alert.body?.trim().orEmpty()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    if (preview.isNotBlank()) {
                                        Text(
                                            preview,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                if (alert.unread == true) {
                                    Text(
                                        "New",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        TextButton(onClick = onNavigateToAlertCenter) {
                            Text("Open Alert Center")
                        }
                    }
                }
                if (state.careInsights.isNotEmpty()) {
                    MindcareSimpleCard {
                        MindcareSectionTitle("Insights")
                        Spacer(Modifier.height(6.dp))
                        state.careInsights.forEach {
                            Text("• $it", style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }
                if (state.trendInsights.isNotEmpty()) {
                    MindcareSimpleCard {
                        MindcareSectionTitle("Trends")
                        Spacer(Modifier.height(6.dp))
                        state.trendInsights.forEach {
                            Text("• $it", style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }
                MindcareSimpleCard {
                    MindcareSectionTitle("Activity")
                    Spacer(Modifier.height(8.dp))
                    if (state.isLoading) {
                        Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else if (state.recent.isEmpty()) {
                        Text("No recent activity yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        state.recent.take(3).forEach { item ->
                            Text(
                                memoryTypeChipLabel(item.type),
                                style = MaterialTheme.typography.labelSmall,
                                color = memoryAccentForType(item.type)
                            )
                            Text(
                                item.title ?: item.type.replace("_", " "),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                            )
                            if (!item.text.isNullOrBlank()) {
                                Text(
                                    item.text,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
                Spacer(Modifier.height(72.dp))
            }
        }
    }
}
