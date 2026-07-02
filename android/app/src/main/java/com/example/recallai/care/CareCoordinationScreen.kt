package com.example.recallai.care

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.recallai.R
import com.example.recallai.data.AuthManager
import com.example.recallai.data.CareRepository
import com.example.recallai.data.remote.CareTaskDto
import com.example.recallai.notifications.RecallNotifications
import com.example.recallai.ui.components.AnimatedAssistChip
import com.example.recallai.ui.components.AppBackdrop
import com.example.recallai.ui.components.GlassCard
import com.example.recallai.ui.components.HeroHeaderCard
import com.example.recallai.ui.components.PrimaryActionButton
import com.example.recallai.ui.components.RecallTopBar
import com.example.recallai.ui.components.SectionTitle
import com.example.recallai.ui.components.TonalActionButton
import com.example.recallai.data.remote.resolveUser
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.json.JSONObject
import retrofit2.HttpException

data class CoordCareTaskRow(
    val id: String,
    val title: String,
    val description: String?,
    val dueLabel: String,
    val priority: String,
    val isDone: Boolean
)

data class CareCoordinationUiState(
    val tasks: List<CoordCareTaskRow> = emptyList(),
    val titleInput: String = "",
    val descriptionInput: String = "",
    val dueInput: String = "Today",
    val timeInput: String = "",
    val priorityInput: String = "MEDIUM",
    val feedbackMessage: String? = null,
    val feedbackIsError: Boolean = false,
    val isLoadingTasks: Boolean = false,
    val isSubmitting: Boolean = false,
    val patientLabel: String? = null,
    val needsPatientSelection: Boolean = false
)

@HiltViewModel
class CareCoordinationViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val careRepository: CareRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(CareCoordinationUiState())
    val uiState = _uiState.asStateFlow()

    private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

    init {
        careRepository.selectedPatientId
            .onEach { refreshTasks() }
            .launchIn(viewModelScope)
    }

    fun onTitleChange(v: String) {
        _uiState.value = _uiState.value.copy(titleInput = v, feedbackMessage = null)
    }

    fun onDescriptionChange(v: String) {
        _uiState.value = _uiState.value.copy(descriptionInput = v, feedbackMessage = null)
    }

    fun onDueChange(v: String) {
        _uiState.value = _uiState.value.copy(dueInput = v, feedbackMessage = null)
    }

    fun onTimeChange(v: String) {
        _uiState.value = _uiState.value.copy(timeInput = v, feedbackMessage = null)
    }

    fun onPriorityChange(v: String) {
        _uiState.value = _uiState.value.copy(priorityInput = v, feedbackMessage = null)
    }

    fun clearFeedback() {
        _uiState.value = _uiState.value.copy(feedbackMessage = null, feedbackIsError = false)
    }

    private fun isCaregiverRole(): Boolean =
        AuthManager.userRole?.equals("caregiver", ignoreCase = true) == true

    private suspend fun resolveOrAutoSelectPatientId(): String? {
        val current = careRepository.selectedPatientId.value?.trim().orEmpty()
        if (current.isNotBlank()) return current
        val watchRows = runCatching { careRepository.watchlist() }.getOrElse { emptyList() }
        if (watchRows.size == 1) {
            val onlyPid = watchRows.firstOrNull()
                ?.relationship
                ?.patientId
                ?.resolveUser()
                ?._id
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
            if (onlyPid != null) {
                careRepository.selectPatient(onlyPid)
                return onlyPid
            }
        }
        return null
    }

    fun refreshTasks() {
        viewModelScope.launch {
            if (!isCaregiverRole()) return@launch
            _uiState.value = _uiState.value.copy(isLoadingTasks = true)
            val patientId = resolveOrAutoSelectPatientId()
            if (patientId.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(
                    tasks = emptyList(),
                    isLoadingTasks = false,
                    patientLabel = null,
                    needsPatientSelection = true,
                    feedbackMessage = "Select a patient on Watchlist before assigning tasks.",
                    feedbackIsError = true
                )
                return@launch
            }
            val patientLabel = resolvePatientLabel(patientId)
            val listResult = runCatching { careRepository.careTasksForCaregiver(patientId) }
            listResult.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    tasks = emptyList(),
                    isLoadingTasks = false,
                    patientLabel = patientLabel,
                    needsPatientSelection = false,
                    feedbackMessage = humanizeRemoteFailure(e),
                    feedbackIsError = true
                )
                return@launch
            }
            val sorted = listResult.getOrDefault(emptyList()).sortedWith(
                compareBy(
                    {
                        when (it.priority?.uppercase()) {
                            "HIGH" -> 0
                            "LOW" -> 2
                            else -> 1
                        }
                    },
                    { it.dueAt ?: "" }
                )
            )
            val zone = ZoneId.systemDefault()
            val rows = sorted.map { dto -> dto.toRow(zone) }
            _uiState.value = _uiState.value.copy(
                tasks = rows,
                isLoadingTasks = false,
                patientLabel = patientLabel,
                needsPatientSelection = false
            )
        }
    }

    private suspend fun resolvePatientLabel(patientId: String): String? {
        val rows = runCatching { careRepository.myPatients() }.getOrElse { emptyList() }
        val rel = rows.firstOrNull {
            it.patientId.resolveUser()?._id?.trim() == patientId.trim()
        } ?: return null
        val user = rel.patientId.resolveUser()
        return user?.name?.trim()?.takeIf { it.isNotEmpty() }
            ?: user?.email?.substringBefore("@")?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun addTask() {
        val s = _uiState.value
        if (s.isSubmitting) return
        val title = s.titleInput.trim()
        if (title.isBlank()) {
            _uiState.value = s.copy(
                feedbackMessage = "Enter a task title.",
                feedbackIsError = true
            )
            return
        }
        if (!isCaregiverRole()) {
            _uiState.value = s.copy(
                feedbackMessage = "Caregiver accounts only.",
                feedbackIsError = true
            )
            return
        }
        viewModelScope.launch {
            val patientId = resolveOrAutoSelectPatientId()
            if (patientId.isNullOrBlank()) {
                _uiState.value = s.copy(
                    needsPatientSelection = true,
                    feedbackMessage = "Select a patient on Watchlist before assigning tasks.",
                    feedbackIsError = true
                )
                return@launch
            }
            val dueIso = buildDueAtIso(s.dueInput, s.timeInput)
            val desc = s.descriptionInput.trim().takeIf { it.isNotBlank() }
            val patientLabel = resolvePatientLabel(patientId)
            _uiState.value = s.copy(isSubmitting = true, feedbackMessage = null, feedbackIsError = false)
            val result = runCatching {
                careRepository.createCareTask(
                    patientId = patientId,
                    title = title,
                    description = desc,
                    priority = s.priorityInput.uppercase(Locale.US),
                    dueAtIso = dueIso
                )
            }
            result.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isSubmitting = false,
                    feedbackMessage = humanizeRemoteFailure(e),
                    feedbackIsError = true
                )
            }
            result.onSuccess {
                RecallNotifications.show(
                    context = appContext,
                    kind = RecallNotifications.Kind.CareTask,
                    title = "Task assigned",
                    body = "Assigned \"$title\" to your patient.",
                    ignoreActivityToggle = true,
                    screenRoute = "care_coordination"
                )
                val who = patientLabel?.let { " for $it" }.orEmpty()
                _uiState.value = _uiState.value.copy(
                    titleInput = "",
                    descriptionInput = "",
                    timeInput = "",
                    isSubmitting = false,
                    feedbackMessage = "Task assigned$who.",
                    feedbackIsError = false,
                    needsPatientSelection = false
                )
                refreshTasks()
            }
        }
    }

    private fun CareTaskDto.toRow(zone: ZoneId): CoordCareTaskRow {
        val done = status?.equals("done", ignoreCase = true) == true || !doneAt.isNullOrBlank()
        return CoordCareTaskRow(
            id = _id,
            title = title,
            description = description,
            dueLabel = formatDueLabel(dueAt, zone),
            priority = priority?.uppercase(Locale.US) ?: "MEDIUM",
            isDone = done
        )
    }

    private fun formatDueLabel(iso: String?, zone: ZoneId): String {
        if (iso.isNullOrBlank()) return "Anytime"
        return runCatching {
            val inst = Instant.parse(iso)
            val dt = LocalDateTime.ofInstant(inst, zone)
            val day = LocalDate.now(zone)
            val taskDay = dt.toLocalDate()
            val prefix = when (taskDay) {
                day -> "Today"
                day.plusDays(1) -> "Tomorrow"
                else -> taskDay.format(DateTimeFormatter.ofPattern("MMM d"))
            }
            "$prefix · ${timeFmt.format(dt)}"
        }.getOrElse { iso }
    }

    private fun buildDueAtIso(dueInput: String, timeInput: String): String? {
        val zone = ZoneId.systemDefault()
        val date = when {
            dueInput.equals("Tonight", ignoreCase = true) -> LocalDate.now(zone)
            dueInput.contains("Tomorrow", ignoreCase = true) -> LocalDate.now(zone).plusDays(1)
            else -> LocalDate.now(zone)
        }
        val t = timeInput.trim()
        if (t.isBlank()) return null
        val time = runCatching {
            LocalTime.parse(t, DateTimeFormatter.ofPattern("h:mm a", Locale.US))
        }.getOrNull() ?: runCatching {
            LocalTime.parse(t, DateTimeFormatter.ofPattern("H:mm", Locale.US))
        }.getOrNull() ?: return null
        return date.atTime(time).atZone(zone).toInstant().toString()
    }

    private fun humanizeRemoteFailure(e: Throwable): String {
        if (e is HttpException) {
            val raw = e.response()?.errorBody()?.use { it.string() }.orEmpty()
            val fromJson = runCatching {
                JSONObject(raw).optString("message").trim()
            }.getOrDefault("")
            val base = fromJson.ifBlank { raw.ifBlank { e.message() } }
            return when (e.code()) {
                400 -> base.ifBlank { "Invalid request." }
                403 -> base.ifBlank { "No permission." }
                else -> base.ifBlank { "Sync failed (${e.code()})." }
            }
        }
        return e.message?.trim()?.takeIf { it.isNotBlank() }
            ?: "Check connection and try again."
    }
}

@Composable
fun CareCoordinationScreen(
    onBack: () -> Unit,
    onNavigateToWatchlist: () -> Unit = {},
    onNavigateHome: () -> Unit = {},
    onNavigateChat: () -> Unit = {},
    onNavigateFace: () -> Unit = {},
    onNavigateMemories: () -> Unit = {},
    onNavigateRecall: () -> Unit = {},
    viewModel: CareCoordinationViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) {
        viewModel.refreshTasks()
    }
    val pendingCount = state.tasks.count { !it.isDone }
    val doneCount = state.tasks.count { it.isDone }
    val highCount = state.tasks.count { !it.isDone && it.priority == "HIGH" }

    AppBackdrop {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.08f),
            topBar = { RecallTopBar(title = "Care tasks", onBack = onBack) }
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
                    title = "Care tasks",
                    subtitle = when {
                        state.patientLabel != null ->
                            "For ${state.patientLabel} · assign and track below"
                        state.needsPatientSelection ->
                            "Select a patient on Watchlist to assign tasks"
                        else -> "Assign tasks and track progress"
                    },
                    illustrationRes = R.drawable.img_tool_caregiver,
                    modifier = Modifier.fillMaxWidth()
                )

                if (state.needsPatientSelection) {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "No patient selected",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Open Watchlist, tap Select on a patient, then return here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        PrimaryActionButton(
                            text = "Open watchlist",
                            onClick = onNavigateToWatchlist,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                state.feedbackMessage?.let { msg ->
                    CareTaskFeedbackBanner(
                        message = msg,
                        isError = state.feedbackIsError,
                        onDismiss = viewModel::clearFeedback
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CareSummaryTile(
                        label = "Total",
                        value = state.tasks.size.toString(),
                        accent = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    CareSummaryTile(
                        label = "Pending",
                        value = pendingCount.toString(),
                        accent = Color(0xFFEF6C00),
                        modifier = Modifier.weight(1f)
                    )
                    CareSummaryTile(
                        label = "High",
                        value = highCount.toString(),
                        accent = Color(0xFFC62828),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CareSummaryTile(
                        label = "Done",
                        value = doneCount.toString(),
                        accent = Color(0xFF2E7D32),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = viewModel::refreshTasks,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Refresh tasks",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                SectionTitle("New task")
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = state.titleInput,
                        onValueChange = viewModel::onTitleChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Task title") },
                        placeholder = { Text("e.g. Take morning medication") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = state.descriptionInput,
                        onValueChange = viewModel::onDescriptionChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Notes (optional)") },
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Priority",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("HIGH" to "High", "MEDIUM" to "Medium", "LOW" to "Low").forEach { (value, label) ->
                            AnimatedAssistChip(
                                label = if (state.priorityInput == value) "• $label" else label,
                                onClick = { viewModel.onPriorityChange(value) }
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Due",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Today", "Tonight", "Tomorrow Morning").forEach { due ->
                            AnimatedAssistChip(
                                label = if (state.dueInput == due) "• $due" else due,
                                onClick = { viewModel.onDueChange(due) }
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = state.timeInput,
                        onValueChange = viewModel::onTimeChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Time (optional)") },
                        placeholder = { Text("5:25 PM") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.height(14.dp))
                    PrimaryActionButton(
                        text = if (state.isSubmitting) "Assigning…" else "Assign task",
                        onClick = viewModel::addTask,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isSubmitting
                    )
                }

                SectionTitle("Assigned tasks")

                if (state.isLoadingTasks) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (state.tasks.isEmpty()) {
                    CareTaskEmptyState()
                } else {
                    state.tasks.forEach { task ->
                        CareTaskCard(task = task)
                    }
                }

                Spacer(Modifier.height(88.dp))
            }
        }
    }
}

@Composable
private fun CareTaskFeedbackBanner(
    message: String,
    isError: Boolean,
    onDismiss: () -> Unit
) {
    val bg = if (isError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val fg = if (isError) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = bg
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = fg,
                modifier = Modifier.weight(1f)
            )
            TonalActionButton(
                text = "OK",
                onClick = onDismiss,
                modifier = Modifier.width(72.dp)
            )
        }
    }
}

@Composable
private fun CareTaskEmptyState() {
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
                Icons.Filled.Schedule,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            )
            Text(
                "No tasks yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Fill in the form above and tap Assign task.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CareTaskCard(task: CoordCareTaskRow) {
    val style = taskPriorityStyle(task.priority)
    val cardShape = MaterialTheme.shapes.large
    val done = task.isDone

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = cardShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = if (!done && task.priority == "HIGH") 5.dp else 2.dp
    ) {
        Column(
            modifier = Modifier
                .border(
                    width = if (!done && task.priority == "HIGH") 2.dp else 1.dp,
                    color = if (done) {
                        Color(0xFF2E7D32).copy(alpha = 0.4f)
                    } else {
                        style.accent.copy(alpha = 0.55f)
                    },
                    shape = cardShape
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(style.accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (done) Icons.Filled.CheckCircle else Icons.Filled.Schedule,
                        contentDescription = null,
                        tint = if (done) Color(0xFF2E7D32) else style.accent,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = task.dueLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                CareStatusChip(
                    text = if (done) "Done" else "Pending",
                    containerColor = if (done) Color(0xFFE8F5E9) else Color(0xFFFFF8E1),
                    contentColor = if (done) Color(0xFF2E7D32) else Color(0xFFF57F17)
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CareStatusChip(
                    text = style.label,
                    containerColor = style.accent.copy(alpha = 0.12f),
                    contentColor = style.accent
                )
            }

            if (!task.description.isNullOrBlank()) {
                Text(
                    text = task.description.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private data class TaskPriorityStyle(val accent: Color, val label: String)

private fun taskPriorityStyle(priority: String): TaskPriorityStyle = when (priority.uppercase()) {
    "HIGH" -> TaskPriorityStyle(Color(0xFFC62828), "High priority")
    "LOW" -> TaskPriorityStyle(Color(0xFF2E7D32), "Low priority")
    else -> TaskPriorityStyle(Color(0xFFEF6C00), "Medium priority")
}
