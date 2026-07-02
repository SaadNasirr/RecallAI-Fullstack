package com.example.recallai.ui.patient

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessAlarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Nightlight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recallai.data.local.PatientAlarmEntity
import com.example.recallai.data.local.PatientAlarmRepeatMode
import com.example.recallai.data.local.ReminderRepeatMode
import com.example.recallai.ui.dashboard.MindcareColors
import com.example.recallai.ui.dashboard.MindcareSectionTitle
import com.example.recallai.ui.screens.PatientAssignedCareTask
import com.example.recallai.ui.screens.PatientHomeUiState
import com.example.recallai.ui.screens.ScheduleSlotItem
import com.example.recallai.ui.screens.UpcomingScheduleItem
import kotlinx.coroutines.delay

private data class TimeBlockStyle(
    val label: String,
    val icon: ImageVector,
    val tint: Color,
    val bg: Color
)

@Composable
fun LivelyPatientAssignedCareTasksSection(
    tasks: List<PatientAssignedCareTask>,
    onDone: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Color.White,
        shadowElevation = 3.dp
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MindcareSectionTitle("Care tasks")
                if (tasks.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "${tasks.count { !it.isDone }} open",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            if (tasks.isEmpty()) {
                LivelyEmptyState(
                    icon = Icons.Outlined.Assignment,
                    message = "No tasks assigned yet",
                    accent = MindcareColors.CardLavender
                )
            } else {
                tasks.forEachIndexed { index, task ->
                    var visible by remember(task.id) { mutableStateOf(false) }
                    LaunchedEffect(task.id) {
                        delay((index * 80L).coerceAtMost(400L))
                        visible = true
                    }
                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn(tween(400)) + slideInHorizontally(
                            initialOffsetX = { it / 3 },
                            animationSpec = tween(450, easing = FastOutSlowInEasing)
                        )
                    ) {
                        LivelyCareTaskCard(task = task, onDone = onDone)
                    }
                }
            }
        }
    }
}

@Composable
private fun LivelyCareTaskCard(
    task: PatientAssignedCareTask,
    onDone: (String) -> Unit
) {
    val priorityColor = when (task.priority.uppercase()) {
        "HIGH" -> Color(0xFFE53935)
        "LOW" -> Color(0xFF43A047)
        else -> Color(0xFFFFA000)
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = tween(120),
        label = "taskScale"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(18.dp),
        color = priorityColor.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(1.dp, priorityColor.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(priorityColor.copy(alpha = 0.35f), priorityColor.copy(alpha = 0.12f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Assignment,
                    contentDescription = null,
                    tint = priorityColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(task.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Surface(shape = RoundedCornerShape(50), color = priorityColor.copy(alpha = 0.15f)) {
                    Text(
                        task.priority.uppercase(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = priorityColor
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "${task.caregiverName} · ${task.dueLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!task.description.isNullOrBlank()) {
                    Text(
                        task.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            }
            if (!task.isDone) {
                Surface(
                    modifier = Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = { onDone(task.id) }
                    ),
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF2E7D32)
                ) {
                    Text(
                        "Done",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            } else {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "Done",
                    tint = Color(0xFF2E7D32),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
fun LivelyPatientScheduleSection(
    state: PatientHomeUiState,
    onAddAlarm: (String, Int, Int, PatientAlarmRepeatMode, Int, Long?) -> Unit,
    onDeleteAlarm: (PatientAlarmEntity) -> Unit,
    onAddReminder: (String, String, Long, ReminderRepeatMode, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var alarmDialogOpen by remember { mutableStateOf(false) }
    var reminderDialogOpen by remember { mutableStateOf(false) }
    var reminderPresetPick by remember { mutableStateOf("Medication") }

    val timeBlocks = listOf(
        TimeBlockStyle("Morning", Icons.Filled.WbSunny, Color(0xFFFFB74D), MindcareColors.CardPeach),
        TimeBlockStyle("Afternoon", Icons.Filled.WbTwilight, Color(0xFF7B6CF5), MindcareColors.CardLavender),
        TimeBlockStyle("Evening", Icons.Outlined.Nightlight, Color(0xFF5C6BC0), Color(0xFFE8E4FE))
    )
    val scheduleItems = listOf(
        state.scheduleMorning,
        state.scheduleAfternoon,
        state.scheduleEvening
    )
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        MindcareSectionTitle("Today")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            timeBlocks.forEachIndexed { index, block ->
                var visible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    delay(index * 100L)
                    visible = true
                }
                AnimatedVisibility(
                    visible = visible,
                    modifier = Modifier.weight(1f),
                    enter = fadeIn(tween(400)) + slideInVertically(
                        initialOffsetY = { it / 4 },
                        animationSpec = tween(450)
                    )
                ) {
                    LivelyTimeBlockCard(
                        style = block,
                        items = scheduleItems[index]
                    )
                }
            }
        }

        if (state.upcomingTomorrow.isNotEmpty()) {
            MindcareSectionTitle("Coming up")
            LivelyComingUpCard(items = state.upcomingTomorrow)
        }

        MindcareSectionTitle("Alarms")
        LivelyAlarmsCard(
            alarms = state.patientAlarms.filter { it.enabled },
            onAddClick = { alarmDialogOpen = true },
            onDelete = onDeleteAlarm
        )

        MindcareSectionTitle("Reminders")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            listOf(
                Triple("Medication", Icons.Filled.LocalPharmacy, MindcareColors.CardPink),
                Triple("Appointment", Icons.Filled.Event, MindcareColors.CardLavender),
                Triple("Water", Icons.Filled.WaterDrop, MindcareColors.CardMint),
                Triple("Exercise", Icons.Filled.Notifications, MindcareColors.CardPeach),
                Triple("Custom", Icons.Filled.Notifications, Color(0xFFE0E0FF))
            ).forEachIndexed { index, (label, icon, color) ->
                var visible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    delay(200L + index * 60L)
                    visible = true
                }
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(350)) + slideInHorizontally(
                        initialOffsetX = { it / 3 },
                        animationSpec = tween(400)
                    )
                ) {
                    LivelyReminderChip(
                        label = label,
                        icon = icon,
                        color = color,
                        onClick = {
                            reminderPresetPick = label
                            reminderDialogOpen = true
                        }
                    )
                }
            }
        }
    }

    if (alarmDialogOpen) {
        AlarmEditorDialog(
            onDismiss = { alarmDialogOpen = false },
            onConfirm = { label, h, m, mode, mask, oneOff ->
                onAddAlarm(label, h, m, mode, mask, oneOff)
                alarmDialogOpen = false
            }
        )
    }
    if (reminderDialogOpen) {
        ReminderEditorDialog(
            preset = reminderPresetPick,
            onDismiss = { reminderDialogOpen = false },
            onConfirm = { preset, note, at, repeat, mask ->
                onAddReminder(preset, note, at, repeat, mask)
                reminderDialogOpen = false
            }
        )
    }
}

@Composable
private fun LivelyComingUpCard(items: List<UpcomingScheduleItem>) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MindcareColors.CardLavender.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Filled.Event,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    "Tomorrow",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MindcareColors.Ink
                )
            }
            items.forEach { item ->
                val periodTint = when (item.periodLabel) {
                    "Morning" -> Color(0xFFFFB74D)
                    "Afternoon" -> Color(0xFF7B6CF5)
                    else -> Color(0xFF5C6BC0)
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White.copy(alpha = 0.88f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(52.dp)
                        ) {
                            Text(
                                item.timeLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = periodTint,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                item.periodLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                item.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MindcareColors.Ink,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LivelyTimeBlockCard(
    style: TimeBlockStyle,
    items: List<ScheduleSlotItem>
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = style.bg,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 108.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(style.icon, contentDescription = null, tint = style.tint, modifier = Modifier.size(22.dp))
            Text(
                text = style.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MindcareColors.Ink,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            if (items.isEmpty()) {
                LivelyPulseDot(color = style.tint.copy(alpha = 0.5f))
                Text(
                    "Free",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = "${items.size} item${if (items.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = style.tint,
                    textAlign = TextAlign.Center
                )
                items.take(2).forEach { item ->
                    Text(
                        text = item.timeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = style.tint,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = MindcareColors.Ink.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 14.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun LivelyPulseDot(color: Color) {
    val infinite = rememberInfiniteTransition(label = "pulse")
    val alpha by infinite.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "dotAlpha"
    )
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}

@Composable
private fun LivelyAlarmsCard(
    alarms: List<PatientAlarmEntity>,
    onAddClick: () -> Unit,
    onDelete: (PatientAlarmEntity) -> Unit
) {
    val infinite = rememberInfiniteTransition(label = "alarmPulse")
    val pulse by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "alarmScale"
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MindcareColors.CardMint.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = pulse
                        scaleY = pulse
                    }
                    .clickable(onClick = onAddClick),
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primary
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Filled.AccessAlarm, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Add alarm", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            alarms.forEach { alarm ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Filled.AccessAlarm,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                formatAlarmSummary(alarm),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        TextButton(onClick = { onDelete(alarm) }) {
                            Text("Remove", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LivelyReminderChip(
    label: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(118.dp)
            .height(96.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = color
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(icon, contentDescription = null, tint = MindcareColors.Ink.copy(alpha = 0.7f))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MindcareColors.Ink
            )
        }
    }
}

@Composable
private fun LivelyEmptyState(
    icon: ImageVector,
    message: String,
    accent: Color
) {
    val infinite = rememberInfiniteTransition(label = "emptyFloat")
    val bob by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "bob"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .graphicsLayer { translationY = -bob }
                .clip(CircleShape)
                .background(accent),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
