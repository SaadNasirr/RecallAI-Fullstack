@file:Suppress("UNUSED_PARAMETER")

package com.example.recallai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.recallai.R
import com.example.recallai.ui.components.AppBackdrop
import com.example.recallai.ui.components.GlassCard
import com.example.recallai.ui.components.HeroHeaderCard
import com.example.recallai.ui.components.PrimaryActionButton
import com.example.recallai.ui.components.RecallTopBar
import com.example.recallai.ui.components.SectionTitle

@Composable
fun CaregiverPatientsScreen(
    onBack: () -> Unit = {},
    onNavigateToTimeline: () -> Unit = {},
    onNavigateToZones: () -> Unit = {},
    onNavigateToCareCoordination: () -> Unit = {},
    onNavigateHome: () -> Unit = {},
    onNavigateChat: () -> Unit = {},
    onNavigateFace: () -> Unit = {},
    onNavigateMemories: () -> Unit = {},
    onNavigateRecall: () -> Unit = {},
    onNavigateToAddPatient: () -> Unit = {},
    viewModel: CaregiverPatientsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var query by remember { mutableStateOf("") }

    val filtered = remember(state.patients, query) {
        state.patients.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
    }
    val activeCount = remember(state.patients) {
        state.patients.count { it.lastActiveLabel.startsWith("Active", ignoreCase = true) }
    }
    val emergencyCount = remember(state.patients) { state.patients.sumOf { it.unreadEmergency } }
    val selectedName = remember(state.patients) {
        state.patients.firstOrNull { it.isSelected }?.name
    }

    fun withPatient(patientId: String, navigate: () -> Unit) {
        viewModel.selectPatientForCaregiving(patientId)
        navigate()
    }

    AppBackdrop {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.08f),
            topBar = { RecallTopBar(title = "Watchlist", onBack = onBack) }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Spacer(Modifier.height(4.dp))

                HeroHeaderCard(
                    title = "Patient watchlist",
                    subtitle = when {
                        state.patients.isEmpty() -> "Link someone you care for to get started."
                        selectedName != null -> "Caring for $selectedName · ${state.patients.size} linked"
                        else -> "${state.patients.size} linked · tap a patient to focus care"
                    },
                    illustrationRes = R.drawable.ill_patient_caregiver,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    WatchlistSummaryTile(
                        label = "Patients",
                        value = state.patients.size.toString(),
                        accent = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    WatchlistSummaryTile(
                        label = "Active",
                        value = activeCount.toString(),
                        accent = Color(0xFF2E7D32),
                        modifier = Modifier.weight(1f)
                    )
                    WatchlistSummaryTile(
                        label = "SOS",
                        value = emergencyCount.toString(),
                        accent = if (emergencyCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PrimaryActionButton(
                        text = "Add patient",
                        onClick = onNavigateToAddPatient,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = viewModel::refresh,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Refresh watchlist",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    placeholder = { Text("Search by name…") },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )

                SectionTitle("Your patients")

                when {
                    state.isLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    state.patients.isEmpty() -> {
                        WatchlistEmptyState(onAddPatient = onNavigateToAddPatient)
                    }

                    filtered.isEmpty() -> {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "No patients match \"$query\".",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    else -> {
                        filtered.forEach { item ->
                            WatchlistPatientCard(
                                item = item,
                                onSelect = { viewModel.selectPatientForCaregiving(item.patientId) },
                                onTasks = { withPatient(item.patientId, onNavigateToCareCoordination) },
                                onZones = { withPatient(item.patientId, onNavigateToZones) },
                                onTimeline = { withPatient(item.patientId, onNavigateToTimeline) }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(88.dp))
            }
        }
    }
}

@Composable
private fun WatchlistSummaryTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun WatchlistEmptyState(onAddPatient: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Outlined.Person,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            )
            Text(
                "No linked patients yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Add a patient with a QR code or invite link, then manage their care from here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            PrimaryActionButton(
                text = "Add your first patient",
                onClick = onAddPatient,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun WatchlistPatientCard(
    item: CaregiverPatientItem,
    onSelect: () -> Unit,
    onTasks: () -> Unit,
    onZones: () -> Unit,
    onTimeline: () -> Unit
) {
    val status = remember(item.lastActiveLabel) { watchlistStatusStyle(item.lastActiveLabel) }
    val taskProgress = remember(item.tasksTodayLabel) { parseTaskProgress(item.tasksTodayLabel) }

    val cardShape = MaterialTheme.shapes.large
    val borderColor = if (item.isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    }
    val borderWidth = if (item.isSelected) 2.dp else 1.dp

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = cardShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = if (item.isSelected) 6.dp else 3.dp
    ) {
        Column(
            modifier = Modifier
                .border(borderWidth, borderColor, cardShape)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.75f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.name.take(2).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (item.unreadEmergency > 0) {
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = CircleShape
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Warning,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        "${item.unreadEmergency}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        text = item.relation.replaceFirstChar { c -> c.uppercaseChar().toString() },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (item.isSelected) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = status.containerColor,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = status.label,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = status.contentColor
                    )
                }
                Text(
                    text = item.tasksTodayLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (taskProgress != null) {
                val (done, total) = taskProgress
                LinearProgressIndicator(
                    progress = { (done.toFloat() / total.coerceAtLeast(1)).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            if (item.locationShort != "—") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Filled.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                    Text(
                        text = item.locationShort,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item.isSelected) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Active patient for care",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                } else {
                    PrimaryActionButton(
                        text = "Select for care",
                        onClick = onSelect,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WatchlistActionTile(
                        label = "Tasks",
                        icon = Icons.Filled.Schedule,
                        onClick = onTasks,
                        modifier = Modifier.weight(1f)
                    )
                    WatchlistActionTile(
                        label = "Zones",
                        icon = Icons.Filled.LocationOn,
                        onClick = onZones,
                        modifier = Modifier.weight(1f)
                    )
                    WatchlistActionTile(
                        label = "Timeline",
                        icon = Icons.Filled.Timeline,
                        onClick = onTimeline,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun WatchlistActionTile(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

private data class WatchlistStatusStyle(
    val label: String,
    val containerColor: Color,
    val contentColor: Color
)

private fun watchlistStatusStyle(lastActiveLabel: String): WatchlistStatusStyle = when {
    lastActiveLabel.startsWith("Active", ignoreCase = true) -> WatchlistStatusStyle(
        label = lastActiveLabel,
        containerColor = Color(0xFFE8F5E9),
        contentColor = Color(0xFF2E7D32)
    )
    lastActiveLabel.startsWith("Waiting", ignoreCase = true) -> WatchlistStatusStyle(
        label = "Awaiting activity",
        containerColor = Color(0xFFECEFF1),
        contentColor = Color(0xFF546E7A)
    )
    else -> WatchlistStatusStyle(
        label = lastActiveLabel,
        containerColor = Color(0xFFFFF8E1),
        contentColor = Color(0xFFF57F17)
    )
}

private fun parseTaskProgress(label: String): Pair<Int, Int>? {
    val match = Regex("""(\d+)\s+of\s+(\d+)""").find(label) ?: return null
    val done = match.groupValues[1].toIntOrNull() ?: return null
    val total = match.groupValues[2].toIntOrNull() ?: return null
    return done to total
}
