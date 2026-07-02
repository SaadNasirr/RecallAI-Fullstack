package com.example.recallai.care

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.recallai.R
import com.example.recallai.data.AuthManager
import com.example.recallai.data.CareRepository
import com.example.recallai.data.remote.CareAlertDto
import com.example.recallai.ui.components.AnimatedAssistChip
import com.example.recallai.ui.components.AppBackdrop
import com.example.recallai.ui.components.HeroHeaderCard
import com.example.recallai.ui.components.PrimaryActionButton
import com.example.recallai.ui.components.RecallTopBar
import com.example.recallai.ui.components.SectionTitle
import com.example.recallai.ui.components.TonalActionButton
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AlertCenterUiState(
    val rows: List<CareAlertDto> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class AlertCenterViewModel @Inject constructor(
    private val careRepository: CareRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(AlertCenterUiState())
    val uiState = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (AuthManager.userRole != "caregiver") {
            _uiState.value = AlertCenterUiState(isLoading = false, rows = emptyList())
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val list = runCatching { careRepository.alertsInbox() }.getOrElse { e ->
                _uiState.value = AlertCenterUiState(isLoading = false, error = e.message)
                return@launch
            }
            _uiState.value = AlertCenterUiState(rows = list, isLoading = false)
        }
    }

    fun markRead(alertId: String?) {
        if (alertId.isNullOrBlank()) return
        viewModelScope.launch {
            runCatching { careRepository.markAlertRead(alertId) }
            refresh()
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            runCatching { careRepository.markAllAlertsRead() }
            refresh()
        }
    }
}

private enum class AlertFilter(val label: String) {
    ALL("All"),
    EMERGENCY("SOS"),
    TASK("Tasks"),
    LOCATION("Zones"),
    MOOD("Mood")
}

@Composable
fun AlertCenterScreen(
    onBack: () -> Unit,
    onNavigateToZones: () -> Unit = {},
    onNavigateToEmergency: () -> Unit = {},
    onNavigateToCareCoordination: () -> Unit = {},
    onNavigateToTimeline: () -> Unit = {},
    viewModel: AlertCenterViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var filter by remember { mutableStateOf(AlertFilter.ALL) }

    val filtered = remember(state.rows, filter) {
        state.rows.filter { dto -> matchesAlertFilter(dto, filter) }
    }
    val unreadCount = remember(state.rows) { state.rows.count { it.unread == true } }
    val emergencyCount = remember(state.rows) {
        state.rows.count { it.type?.lowercase(Locale.US)?.contains("emergency") == true }
    }

    fun openAlert(dto: CareAlertDto) {
        viewModel.markRead(dto._id)
        when (dto.type?.lowercase(Locale.US)) {
            "emergency" -> onNavigateToEmergency()
            "missed_task" -> onNavigateToCareCoordination()
            "location_share" -> onNavigateToZones()
            "mood_checkin" -> onNavigateToTimeline()
            else -> onNavigateToTimeline()
        }
    }

    AppBackdrop {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.08f),
            topBar = { RecallTopBar(title = "Alerts", onBack = onBack) }
        ) { padding ->
            when {
                state.isLoading -> {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                state.error != null -> {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(state.error ?: "", color = MaterialTheme.colorScheme.error)
                    }
                }

                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(horizontal = 16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Spacer(Modifier.height(4.dp))

                            HeroHeaderCard(
                                title = "Alert center",
                                subtitle = when {
                                    state.rows.isEmpty() -> "Risk signals from your patients appear here."
                                    unreadCount > 0 -> "$unreadCount unread · review in order below"
                                    else -> "${state.rows.size} alerts · all caught up"
                                },
                                illustrationRes = R.drawable.img_tool_emergency,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CareSummaryTile(
                                    label = "Total",
                                    value = state.rows.size.toString(),
                                    accent = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                CareSummaryTile(
                                    label = "Unread",
                                    value = unreadCount.toString(),
                                    accent = if (unreadCount > 0) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        Color(0xFF2E7D32)
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                CareSummaryTile(
                                    label = "SOS",
                                    value = emergencyCount.toString(),
                                    accent = Color(0xFFC62828),
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TonalActionButton(
                                    text = "Mark all read",
                                    onClick = viewModel::clearAll,
                                    modifier = Modifier.weight(1f),
                                    enabled = unreadCount > 0
                                )
                                IconButton(
                                    onClick = viewModel::refresh,
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                        )
                                ) {
                                    Icon(
                                        Icons.Filled.Refresh,
                                        contentDescription = "Refresh alerts",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            SectionTitle("Filter")
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AlertFilter.entries.forEach { f ->
                                    AnimatedAssistChip(
                                        label = if (filter == f) "• ${f.label}" else f.label,
                                        onClick = { filter = f }
                                    )
                                }
                            }

                            SectionTitle(
                                if (filter == AlertFilter.ALL) "All alerts" else "${filter.label} alerts"
                            )

                            if (filtered.isEmpty()) {
                                AlertEmptyState(hasAny = state.rows.isNotEmpty())
                            } else {
                                filtered.forEach { dto ->
                                    AlertCard(
                                        dto = dto,
                                        onOpen = { openAlert(dto) },
                                        onMarkRead = { viewModel.markRead(dto._id) }
                                    )
                                }
                            }

                            Spacer(Modifier.height(24.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertEmptyState(hasAny: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Outlined.NotificationsNone,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            )
            Text(
                text = if (hasAny) "No alerts in this filter" else "No alerts yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (hasAny) {
                    "Try another filter chip above."
                } else {
                    "Emergencies, missed tasks, and mood updates will show here."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AlertCard(
    dto: CareAlertDto,
    onOpen: () -> Unit,
    onMarkRead: () -> Unit
) {
    val style = alertVisualStyle(dto.type)
    val unread = dto.unread == true
    val cardShape = MaterialTheme.shapes.large
    val borderColor = if (unread) style.accent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val borderWidth = if (unread) 2.dp else 1.dp

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = cardShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = if (unread) 5.dp else 2.dp
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
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(style.accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = style.icon,
                        contentDescription = null,
                        tint = style.accent,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = (dto.title ?: dto.type ?: "Alert").trim(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (unread) {
                            Spacer(Modifier.width(6.dp))
                            CareStatusChip(
                                text = "New",
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    CareStatusChip(
                        text = style.typeLabel,
                        containerColor = style.accent.copy(alpha = 0.12f),
                        contentColor = style.accent
                    )
                }
            }

            val body = dto.body.orEmpty().trim()
            if (body.isNotBlank()) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = formatRelativeTime(parseRemoteAlertTime(dto.createdAt)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PrimaryActionButton(
                    text = style.openLabel,
                    onClick = onOpen,
                    modifier = Modifier.weight(1f)
                )
                if (unread) {
                    TonalActionButton(
                        text = "Read",
                        onClick = onMarkRead,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

private data class AlertVisualStyle(
    val accent: Color,
    val icon: ImageVector,
    val typeLabel: String,
    val openLabel: String
)

private fun alertVisualStyle(type: String?): AlertVisualStyle {
    val t = type?.lowercase(Locale.US).orEmpty()
    return when {
        t.contains("emergency") -> AlertVisualStyle(
            Color(0xFFC62828),
            Icons.Filled.Emergency,
            "Emergency",
            "Open SOS"
        )
        t.contains("missed") -> AlertVisualStyle(
            Color(0xFFEF6C00),
            Icons.Filled.Schedule,
            "Missed task",
            "Open tasks"
        )
        t.contains("location") -> AlertVisualStyle(
            Color(0xFF1565C0),
            Icons.Filled.LocationOn,
            "Location",
            "Open zones"
        )
        t.contains("mood") -> AlertVisualStyle(
            Color(0xFF2E7D32),
            Icons.Filled.Mood,
            "Mood",
            "Open timeline"
        )
        else -> AlertVisualStyle(
            Color(0xFF546E7A),
            Icons.Filled.Timeline,
            "Update",
            "Open"
        )
    }
}

private fun matchesAlertFilter(dto: CareAlertDto, filter: AlertFilter): Boolean {
    if (filter == AlertFilter.ALL) return true
    val t = dto.type?.lowercase(Locale.US).orEmpty()
    return when (filter) {
        AlertFilter.EMERGENCY -> t.contains("emergency")
        AlertFilter.TASK -> t.contains("missed") || t.contains("task")
        AlertFilter.LOCATION -> t.contains("location")
        AlertFilter.MOOD -> t.contains("mood")
        AlertFilter.ALL -> true
    }
}

private fun parseRemoteAlertTime(iso: String?): Long {
    if (iso.isNullOrBlank()) return System.currentTimeMillis()
    return runCatching { Instant.parse(iso).toEpochMilli() }.getOrElse {
        runCatching { OffsetDateTime.parse(iso).toInstant().toEpochMilli() }.getOrElse {
            System.currentTimeMillis()
        }
    }
}

private fun formatRelativeTime(epochMs: Long): String {
    val sec = ((System.currentTimeMillis() - epochMs) / 1000L).coerceAtLeast(0L)
    return when {
        sec < 60L -> "${sec}s ago"
        sec < 3600L -> "${sec / 60L} min ago"
        sec < 86400L -> {
            val h = sec / 3600L
            if (h == 1L) "1 hour ago" else "$h hours ago"
        }
        else -> "${sec / 86400L}d ago"
    }
}
