package com.example.recallai.ui.patient

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.recallai.data.local.PatientAlarmRepeatMode
import com.example.recallai.data.local.ReminderRepeatMode
import com.example.recallai.reminders.ReminderScheduleHelper
import com.example.recallai.reminders.ReminderUiFormatter
import com.example.recallai.ui.dashboard.MindcareColors
import java.util.Calendar
import java.util.Locale

private enum class ReminderRepeatUi {
    NONE, DAILY, WEEKLY, CUSTOM_DAYS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AlarmEditorDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Int, Int, PatientAlarmRepeatMode, Int, Long?) -> Unit
) {
    var label by remember { mutableStateOf("") }
    var hour by remember { mutableIntStateOf(8) }
    var minute by remember { mutableIntStateOf(0) }
    var useSpecificDate by remember { mutableStateOf(true) }
    var dayMask by remember { mutableIntStateOf(0) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedDateMillis by remember {
        mutableLongStateOf(
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        )
    }

    val mode = if (useSpecificDate) PatientAlarmRepeatMode.ONCE else PatientAlarmRepeatMode.WEEKLY
    val oneOffMillis = if (useSpecificDate) {
        ReminderScheduleHelper.combineLocalDateAndTime(selectedDateMillis, hour, minute)
    } else null

    val scheduleLabel =
        if (useSpecificDate) "One-time alarm" else {
            val names = ReminderUiFormatter.maskToLabel(dayMask)
            if (names.isBlank()) "Repeats (pick days)" else "Repeats every $names"
        }

    val preview = if (useSpecificDate) {
        val cal = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
        val d = cal.get(Calendar.DAY_OF_MONTH)
        val mo = cal.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault())
        val y = cal.get(Calendar.YEAR)
        val ampm = if (hour >= 12) "PM" else "AM"
        val h12 = when (val h = hour % 12) { 0 -> 12; else -> h }
        "Rings once on $d $mo $y at $h12:${minute.toString().padStart(2, '0')} $ampm"
    } else {
        val names = ReminderUiFormatter.maskToLabel(dayMask)
        val ampm = if (hour >= 12) "PM" else "AM"
        val h12 = when (val h = hour % 12) { 0 -> 12; else -> h }
        if (names.isBlank()) {
            "Pick at least one day for a recurring alarm"
        } else {
            "Rings on $names at $h12:${minute.toString().padStart(2, '0')} $ampm"
        }
    }

    MindcareScheduleDialogShell(
        title = "Alarm",
        onDismiss = onDismiss,
        onConfirm = {
            if (!useSpecificDate && dayMask == 0) return@MindcareScheduleDialogShell
            onConfirm(label, hour, minute, mode, dayMask, oneOffMillis)
        },
        confirmEnabled = useSpecificDate || dayMask != 0
    ) {
        MindcareScheduleTextField(
            value = label,
            onValueChange = { label = it },
            label = "Label"
        )
        MindcareSchedulePickerRow(
            icon = Icons.Filled.AccessTime,
            title = "Time",
            value = formatTime12(hour, minute),
            onClick = { showTimePicker = true }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MindcareOptionPill("Specific date", useSpecificDate) { useSpecificDate = true }
            MindcareOptionPill("Recurring", !useSpecificDate) { useSpecificDate = false }
        }
        if (useSpecificDate) {
            MindcareSchedulePickerRow(
                icon = Icons.Filled.EditCalendar,
                title = "Date",
                value = ReminderUiFormatter.formatEpochDate(selectedDateMillis),
                onClick = { showDatePicker = true }
            )
        } else {
            MindcareDayChipRow(selectedMask = dayMask, onMaskChange = { dayMask = it })
        }
        MindcareSchedulePreviewCard(title = scheduleLabel, body = preview)
    }

    if (showTimePicker) {
        MindcareTimePickerOverlay(
            initialHour = hour,
            initialMinute = minute,
            onDismiss = { showTimePicker = false },
            onConfirm = { h, m ->
                hour = h
                minute = m
                showTimePicker = false
            }
        )
    }
    if (showDatePicker) {
        MindcareDatePickerOverlay(
            initialMillis = selectedDateMillis,
            onDismiss = { showDatePicker = false },
            onConfirm = {
                selectedDateMillis = it
                showDatePicker = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReminderEditorDialog(
    preset: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Long, ReminderRepeatMode, Int) -> Unit
) {
    var note by remember { mutableStateOf("") }
    var hour by remember { mutableIntStateOf(9) }
    var minute by remember { mutableIntStateOf(0) }
    var repeatUi by remember { mutableStateOf(ReminderRepeatUi.NONE) }
    var dayMask by remember { mutableIntStateOf(0) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedDateMillis by remember {
        mutableLongStateOf(
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        )
    }

    val repeatMode = when (repeatUi) {
        ReminderRepeatUi.NONE -> ReminderRepeatMode.NONE
        ReminderRepeatUi.DAILY -> ReminderRepeatMode.DAILY
        ReminderRepeatUi.WEEKLY,
        ReminderRepeatUi.CUSTOM_DAYS -> ReminderRepeatMode.WEEKLY
    }
    val mask = when (repeatUi) {
        ReminderRepeatUi.WEEKLY,
        ReminderRepeatUi.CUSTOM_DAYS -> dayMask
        else -> 0
    }

    val atMillis: Long = remember(repeatMode, mask, hour, minute, selectedDateMillis) {
        when (repeatMode) {
            ReminderRepeatMode.NONE ->
                ReminderScheduleHelper.combineLocalDateAndTime(selectedDateMillis, hour, minute)
            ReminderRepeatMode.DAILY ->
                ReminderScheduleHelper.firstDailyTrigger(hour, minute, System.currentTimeMillis())
            ReminderRepeatMode.WEEKLY ->
                ReminderScheduleHelper.firstWeeklyTrigger(hour, minute, mask, System.currentTimeMillis())
                    ?: ReminderScheduleHelper.combineLocalDateAndTime(selectedDateMillis, hour, minute)
        }
    }

    val summary = remember(repeatUi, dayMask, hour, minute, selectedDateMillis, preset, note) {
        val cal = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
        val dayName = cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault())
        val d = cal.get(Calendar.DAY_OF_MONTH)
        val mo = cal.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault())
        val ampm = if (hour >= 12) "PM" else "AM"
        val h12 = when (val h = hour % 12) { 0 -> 12; else -> h }
        val timeStr = "$h12:${minute.toString().padStart(2, '0')} $ampm"
        when (repeatUi) {
            ReminderRepeatUi.NONE -> "Remind me on $dayName $d $mo at $timeStr"
            ReminderRepeatUi.DAILY -> "Remind me every day at $timeStr"
            ReminderRepeatUi.WEEKLY,
            ReminderRepeatUi.CUSTOM_DAYS -> {
                val names = ReminderUiFormatter.maskToLabel(dayMask)
                if (names.isBlank()) "Pick days for a weekly reminder"
                else "Remind me every $names at $timeStr"
            }
        }
    }

    val needsDays = repeatUi == ReminderRepeatUi.WEEKLY || repeatUi == ReminderRepeatUi.CUSTOM_DAYS
    val title = if (preset == "Custom") "Custom reminder" else preset

    MindcareScheduleDialogShell(
        title = title,
        onDismiss = onDismiss,
        onConfirm = {
            if (needsDays && dayMask == 0) return@MindcareScheduleDialogShell
            val finalPreset = if (preset == "Custom") note.trim().ifBlank { "Reminder" } else preset
            val desc = if (preset == "Custom") "" else note
            onConfirm(finalPreset, desc, atMillis, repeatMode, mask)
        },
        confirmEnabled = !needsDays || dayMask != 0
    ) {
        if (preset != "Custom") {
            MindcareScheduleTextField(value = note, onValueChange = { note = it }, label = "Note")
        } else {
            MindcareScheduleTextField(value = note, onValueChange = { note = it }, label = "Title")
        }
        MindcareSchedulePickerRow(
            icon = Icons.Filled.AccessTime,
            title = "Time",
            value = formatTime12(hour, minute),
            onClick = { showTimePicker = true }
        )
        if (repeatUi == ReminderRepeatUi.NONE) {
            MindcareSchedulePickerRow(
                icon = Icons.Filled.EditCalendar,
                title = "Date",
                value = ReminderUiFormatter.formatEpochDate(selectedDateMillis),
                onClick = { showDatePicker = true }
            )
        }
        Text(
            "Repeat",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MindcareColors.Ink
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MindcareOptionPill("Once", repeatUi == ReminderRepeatUi.NONE) { repeatUi = ReminderRepeatUi.NONE }
            MindcareOptionPill("Daily", repeatUi == ReminderRepeatUi.DAILY) { repeatUi = ReminderRepeatUi.DAILY }
            MindcareOptionPill("Weekly", repeatUi == ReminderRepeatUi.WEEKLY) { repeatUi = ReminderRepeatUi.WEEKLY }
            MindcareOptionPill("Custom", repeatUi == ReminderRepeatUi.CUSTOM_DAYS) {
                repeatUi = ReminderRepeatUi.CUSTOM_DAYS
            }
        }
        if (needsDays) {
            MindcareDayChipRow(selectedMask = dayMask, onMaskChange = { dayMask = it })
        }
        MindcareSchedulePreviewCard(title = "Summary", body = summary)
    }

    if (showTimePicker) {
        MindcareTimePickerOverlay(
            initialHour = hour,
            initialMinute = minute,
            onDismiss = { showTimePicker = false },
            onConfirm = { h, m ->
                hour = h
                minute = m
                showTimePicker = false
            }
        )
    }
    if (showDatePicker) {
        MindcareDatePickerOverlay(
            initialMillis = selectedDateMillis,
            onDismiss = { showDatePicker = false },
            onConfirm = {
                selectedDateMillis = it
                showDatePicker = false
            }
        )
    }
}

@Composable
private fun MindcareScheduleDialogShell(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean = true,
    confirmLabel: String = "Save",
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            shadowElevation = 10.dp
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MindcareColors.NavPill)
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MindcareColors.CardLavender.copy(alpha = 0.5f),
                                    Color.White
                                )
                            )
                        )
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            "Cancel",
                            color = MindcareColors.Ink.copy(alpha = 0.65f),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        modifier = Modifier.clickable(enabled = confirmEnabled) {
                            if (confirmEnabled) onConfirm()
                        },
                        shape = RoundedCornerShape(50),
                        color = if (confirmEnabled) {
                            MindcareColors.Ink
                        } else {
                            MindcareColors.Ink.copy(alpha = 0.35f)
                        }
                    ) {
                        Text(
                            confirmLabel,
                            modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MindcareScheduleTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = MindcareColors.Ink.copy(alpha = 0.6f)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MindcareColors.Ink.copy(alpha = 0.15f),
            cursorColor = MaterialTheme.colorScheme.primary,
            focusedTextColor = MindcareColors.Ink,
            unfocusedTextColor = MindcareColors.Ink
        )
    )
}

@Composable
private fun MindcareSchedulePickerRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, MindcareColors.Ink.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MindcareColors.Ink.copy(alpha = 0.55f)
                )
                Text(
                    value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MindcareColors.Ink
                )
            }
        }
    }
}

@Composable
private fun MindcareOptionPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        color = if (selected) MindcareColors.Ink else Color.White,
        border = if (selected) null else BorderStroke(1.dp, MindcareColors.Ink.copy(alpha = 0.2f))
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
            color = if (selected) Color.White else MindcareColors.Ink,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun MindcareDayChipRow(selectedMask: Int, onMaskChange: (Int) -> Unit) {
    val labels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        labels.forEachIndexed { index, ch ->
            val bit = 1 shl index
            val on = (selectedMask and bit) != 0
            Surface(
                modifier = Modifier
                    .height(40.dp)
                    .clickable {
                        onMaskChange(if (on) selectedMask and bit.inv() else selectedMask or bit)
                    },
                shape = RoundedCornerShape(50),
                color = if (on) MindcareColors.Ink else Color.White,
                border = if (!on) BorderStroke(1.dp, MindcareColors.Ink.copy(alpha = 0.2f)) else null
            ) {
                Text(
                    ch,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    color = if (on) Color.White else MindcareColors.Ink
                )
            }
        }
    }
}

@Composable
private fun MindcareSchedulePreviewCard(title: String, body: String) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MindcareColors.CardMint.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MindcareColors.Ink.copy(alpha = 0.06f))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MindcareColors.Ink
            )
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MindcareColors.Ink.copy(alpha = 0.75f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MindcareTimePickerOverlay(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = false
    )
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            shadowElevation = 10.dp
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MindcareColors.NavPill)
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    Text(
                        text = formatTime12(state.hour, state.minute),
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                TimePicker(
                    state = state,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = MindcareColors.CardLavender,
                        clockDialSelectedContentColor = Color.White,
                        clockDialUnselectedContentColor = MindcareColors.Ink,
                        selectorColor = MaterialTheme.colorScheme.primary,
                        timeSelectorSelectedContainerColor = MindcareColors.Ink,
                        timeSelectorUnselectedContainerColor = MindcareColors.CardLavender,
                        timeSelectorSelectedContentColor = Color.White,
                        timeSelectorUnselectedContentColor = MindcareColors.Ink,
                        periodSelectorSelectedContainerColor = MindcareColors.Ink,
                        periodSelectorUnselectedContainerColor = MindcareColors.CardLavender,
                        periodSelectorSelectedContentColor = Color.White,
                        periodSelectorUnselectedContentColor = MindcareColors.Ink
                    )
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = MindcareColors.Ink.copy(alpha = 0.65f))
                    }
                    TextButton(onClick = { onConfirm(state.hour, state.minute) }) {
                        Text("OK", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MindcareDatePickerOverlay(
    initialMillis: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            shadowElevation = 10.dp
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MindcareColors.NavPill)
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = "Pick date",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                DatePicker(
                    state = state,
                    colors = DatePickerDefaults.colors(
                        selectedDayContainerColor = MindcareColors.Ink,
                        selectedDayContentColor = Color.White,
                        todayDateBorderColor = MaterialTheme.colorScheme.primary,
                        todayContentColor = MaterialTheme.colorScheme.primary,
                        dayContentColor = MindcareColors.Ink
                    )
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = MindcareColors.Ink.copy(alpha = 0.65f))
                    }
                    TextButton(
                        onClick = {
                            val picked = state.selectedDateMillis ?: initialMillis
                            onConfirm(picked)
                        }
                    ) {
                        Text("OK", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun formatTime12(hour: Int, minute: Int): String {
    val ampm = if (hour >= 12) "PM" else "AM"
    val h12 = when (val h = hour % 12) { 0 -> 12; else -> h }
    return "$h12:${minute.toString().padStart(2, '0')} $ampm"
}
