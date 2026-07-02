package com.example.recallai.routine

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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.recallai.R
import com.example.recallai.data.CareToolkitRepository
import com.example.recallai.data.MemoryRepository
import com.example.recallai.data.RoutineTaskItem
import com.example.recallai.ui.components.AppBackdrop
import com.example.recallai.ui.components.GlassCard
import com.example.recallai.ui.components.HeroHeaderCard
import com.example.recallai.ui.components.PrimaryActionButton
import com.example.recallai.ui.components.QuickSwitchRow
import com.example.recallai.ui.components.RecallTopBar
import com.example.recallai.ui.components.SectionTitle
import com.example.recallai.ui.components.StatPill
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val periods = listOf("Morning", "Afternoon", "Evening", "Night")

private fun todayDateString(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

// ── ViewModel ──────────────────────────────────────────────────────────────

data class RoutineUiState(
    val tasks: List<RoutineTaskItem> = emptyList(),
    val titleInput: String = "",
    val timeLabelInput: String = "",
    val periodInput: String = "Morning",
    val frequencyInput: String = "Daily",
    val info: String? = null
) {
    val doneCount: Int get() = tasks.count { it.doneToday }
    val pendingCount: Int get() = tasks.count { !it.doneToday }
    val progress: Float get() = if (tasks.isEmpty()) 0f else doneCount.toFloat() / tasks.size
    val totalStreak: Int get() = tasks.sumOf { it.streakDays }
}

@HiltViewModel
class RoutinePlannerViewModel @Inject constructor(
    private val toolkitRepository: CareToolkitRepository,
    private val memoryRepository: MemoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RoutineUiState(tasks = toolkitRepository.getRoutineTasks()))
    val uiState: StateFlow<RoutineUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            toolkitRepository.syncPatientToolkitFromServer()
            _uiState.update { it.copy(tasks = toolkitRepository.getRoutineTasks()) }
        }
    }

    fun onTitleChange(v: String) = _uiState.update { it.copy(titleInput = v, info = null) }
    fun onTimeLabelChange(v: String) = _uiState.update { it.copy(timeLabelInput = v, info = null) }
    fun onPeriodChange(v: String) = _uiState.update { it.copy(periodInput = v, info = null) }
    fun onFrequencyChange(v: String) = _uiState.update { it.copy(frequencyInput = v, info = null) }

    fun addTask() {
        val s = _uiState.value
        val title = s.titleInput.trim()
        if (title.isBlank()) {
            _uiState.update { it.copy(info = "Enter a task title.") }
            return
        }
        val item = RoutineTaskItem(
            id = UUID.randomUUID().toString(),
            title = title,
            period = s.periodInput,
            frequency = s.frequencyInput,
            timeLabel = s.timeLabelInput.trim(),
            updatedAt = System.currentTimeMillis()
        )
        val updated = s.tasks + item
        viewModelScope.launch {
            toolkitRepository.saveRoutineTasks(updated)
            _uiState.update {
                it.copy(tasks = updated, titleInput = "", timeLabelInput = "", info = "Task added.")
            }
            memoryRepository.saveTextMemory(
                text = "Routine task added: ${item.title} (${item.frequency}, ${item.period}${if (item.timeLabel.isNotBlank()) " at ${item.timeLabel}" else ""}).",
                title = "Smart Routine",
                type = "ROUTINE_LOG",
                tags = listOf("routine", item.frequency.lowercase())
            )
        }
    }

    fun toggleDone(id: String) {
        val s = _uiState.value
        val today = todayDateString()
        val updated = s.tasks.map { task ->
            if (task.id != id) return@map task
            val nowDone = !task.doneToday
            val newStreak = if (nowDone) {
                if (task.lastCompletedDate == today) task.streakDays
                else if (task.lastCompletedDate.isNotBlank()) task.streakDays + 1
                else 1
            } else {
                maxOf(0, task.streakDays - 1)
            }
            task.copy(
                doneToday = nowDone,
                updatedAt = System.currentTimeMillis(),
                streakDays = newStreak,
                lastCompletedDate = if (nowDone) today else task.lastCompletedDate
            )
        }
        viewModelScope.launch {
            toolkitRepository.saveRoutineTasks(updated)
            _uiState.update { it.copy(tasks = updated) }
            val changed = updated.firstOrNull { it.id == id } ?: return@launch
            memoryRepository.saveTextMemory(
                text = "Routine task \"${changed.title}\" marked ${if (changed.doneToday) "complete" else "incomplete"} (streak: ${changed.streakDays} day(s)).",
                title = "Routine Check-in",
                type = "ROUTINE_LOG",
                tags = listOf("routine", "daily")
            )
        }
    }

    fun removeTask(id: String) {
        val updated = _uiState.value.tasks.filterNot { it.id == id }
        viewModelScope.launch {
            toolkitRepository.saveRoutineTasks(updated)
            _uiState.update { it.copy(tasks = updated, info = "Task removed.") }
        }
    }
}

// ── Screen ────────────────────────────────────────────────────────────────

@Composable
fun RoutinePlannerScreen(
    onBack: () -> Unit,
    onNavigateHome: () -> Unit = {},
    onNavigateChat: () -> Unit = {},
    onNavigateFace: () -> Unit = {},
    onNavigateMemories: () -> Unit = {},
    onNavigateRecall: () -> Unit = {},
    viewModel: RoutinePlannerViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    AppBackdrop {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = { RecallTopBar(title = "Smart Routine", onBack = onBack) }
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
                    title = "Smart Routine Builder",
                    subtitle = "Build healthy habits and track your daily streaks",
                    illustrationRes = R.drawable.img_tool_routine
                )

                // Stats
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatPill("Tasks", state.tasks.size.toString())
                    StatPill("Done", state.doneCount.toString())
                    StatPill("Pending", state.pendingCount.toString())
                    if (state.totalStreak > 0) StatPill("Streak", "${state.totalStreak}d")
                }

                // Progress
                if (state.tasks.isNotEmpty()) {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Routine Progress",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "${(state.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    state.progress >= 0.8f -> Color(0xFF43A047)
                                    state.progress >= 0.5f -> Color(0xFFFB8C00)
                                    else -> MaterialTheme.colorScheme.primary
                                }
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = when {
                                state.progress >= 0.8f -> Color(0xFF43A047)
                                state.progress >= 0.5f -> Color(0xFFFB8C00)
                                else -> MaterialTheme.colorScheme.primary
                            },
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            strokeCap = StrokeCap.Round
                        )
                    }
                }

                QuickSwitchRow(
                    onHome = onNavigateHome,
                    onChat = onNavigateChat,
                    onFace = onNavigateFace,
                    onMemories = onNavigateMemories,
                    onRecall = onNavigateRecall
                )

                // Add task form
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    SectionTitle("Add Routine Task")
                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = state.titleInput,
                        onValueChange = viewModel::onTitleChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Task Name *") },
                        leadingIcon = { Icon(Icons.Default.Notes, null) },
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.timeLabelInput,
                        onValueChange = viewModel::onTimeLabelChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Time (optional, e.g. 7:00 AM)") },
                        leadingIcon = { Icon(Icons.Default.AccessTime, null) },
                        singleLine = true
                    )

                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Period",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        periods.forEach { period ->
                            FilterChip(
                                selected = state.periodInput == period,
                                onClick = { viewModel.onPeriodChange(period) },
                                label = { Text(period) }
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Repeat",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Daily", "Weekly", "Monthly").forEach { freq ->
                            FilterChip(
                                selected = state.frequencyInput == freq,
                                onClick = { viewModel.onFrequencyChange(freq) },
                                label = { Text(freq) }
                            )
                        }
                    }

                    if (!state.info.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            state.info ?: "",
                            color = if (state.info?.contains("removed", ignoreCase = true) == true)
                                MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    PrimaryActionButton(
                        text = "Add to Routine",
                        onClick = viewModel::addTask,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Grouped task list
                if (state.tasks.isNotEmpty()) {
                    val grouped = state.tasks.groupBy { it.period }
                    periods.forEach { period ->
                        val tasks = grouped[period] ?: return@forEach
                        SectionTitle(period)
                        tasks.forEach { task ->
                            RoutineTaskCard(
                                task = task,
                                onToggle = { viewModel.toggleDone(task.id) },
                                onRemove = { viewModel.removeTask(task.id) }
                            )
                        }
                    }
                    // Tasks with unrecognized period
                    val ungrouped = state.tasks.filter { it.period !in periods }
                    if (ungrouped.isNotEmpty()) {
                        SectionTitle("Other")
                        ungrouped.forEach { task ->
                            RoutineTaskCard(
                                task = task,
                                onToggle = { viewModel.toggleDone(task.id) },
                                onRemove = { viewModel.removeTask(task.id) }
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
private fun RoutineTaskCard(
    task: RoutineTaskItem,
    onToggle: () -> Unit,
    onRemove: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            // Checkmark circle
            Icon(
                imageVector = if (task.doneToday) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = if (task.doneToday) "Done" else "Pending",
                tint = if (task.doneToday) Color(0xFF43A047) else MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(22.dp)
                    .selectable(selected = task.doneToday, onClick = onToggle)
            )
            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = if (task.doneToday) TextDecoration.LineThrough else TextDecoration.None,
                        color = if (task.doneToday)
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (task.streakDays >= 2) {
                        Spacer(Modifier.width(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.LocalFireDepartment,
                                null,
                                tint = Color(0xFFFB8C00),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                "${task.streakDays}d",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFB8C00)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (task.timeLabel.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AccessTime,
                                null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                task.timeLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                when (task.frequency) {
                                    "Weekly" -> Color(0xFF1565C0).copy(alpha = 0.12f)
                                    "Monthly" -> Color(0xFF6A1B9A).copy(alpha = 0.12f)
                                    else -> Color(0xFF2E7D32).copy(alpha = 0.12f)
                                }
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            task.frequency,
                            style = MaterialTheme.typography.labelSmall,
                            color = when (task.frequency) {
                                "Weekly" -> Color(0xFF1565C0)
                                "Monthly" -> Color(0xFF6A1B9A)
                                else -> Color(0xFF2E7D32)
                            }
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AssistChip(
                        onClick = onToggle,
                        label = { Text(if (task.doneToday) "Undo" else "Complete", fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(
                                if (task.doneToday) Icons.Default.RadioButtonUnchecked else Icons.Default.CheckCircle,
                                null,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (task.doneToday)
                                MaterialTheme.colorScheme.surface
                            else Color(0xFF43A047).copy(alpha = 0.12f),
                            labelColor = if (task.doneToday)
                                MaterialTheme.colorScheme.onSurface
                            else Color(0xFF43A047),
                            leadingIconContentColor = if (task.doneToday)
                                MaterialTheme.colorScheme.onSurface
                            else Color(0xFF43A047)
                        )
                    )
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
