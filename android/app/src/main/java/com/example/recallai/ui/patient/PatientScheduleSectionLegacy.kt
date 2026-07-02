package com.example.recallai.ui.patient

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessAlarm
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.recallai.data.local.PatientAlarmEntity
import com.example.recallai.data.local.PatientAlarmRepeatMode
import com.example.recallai.data.local.ReminderRepeatMode
import com.example.recallai.reminders.ReminderUiFormatter
import com.example.recallai.ui.components.GlassCard
import com.example.recallai.ui.components.SectionTitle
import com.example.recallai.ui.screens.PatientHomeUiState
import com.example.recallai.ui.screens.ScheduleSlotItem
import java.util.Locale

private val reminderPresets = listOf(
    "Medication",
    "Appointment",
    "Water",
    "Exercise",
    "Custom"
)

/** Frozen copy before lively schedule UI. Restored when [PatientCareUiLayout.USE_LIVELY_CARE_UI] is false. */
@Composable
fun PatientScheduleSectionLegacy(
    state: PatientHomeUiState,
    onAddAlarm: (String, Int, Int, PatientAlarmRepeatMode, Int, Long?) -> Unit,
    onDeleteAlarm: (PatientAlarmEntity) -> Unit,
    onAddReminder: (String, String, Long, ReminderRepeatMode, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var alarmDialogOpen by remember { mutableStateOf(false) }
    var reminderDialogOpen by remember { mutableStateOf(false) }
    var reminderPresetPick by remember { mutableStateOf(reminderPresets.first()) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("Today")
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            ScheduleChunk("Morning", state.scheduleMorning)
            Spacer(Modifier.height(8.dp))
            ScheduleChunk("Afternoon", state.scheduleAfternoon)
            Spacer(Modifier.height(8.dp))
            ScheduleChunk("Evening", state.scheduleEvening)
        }

        if (state.upcomingTomorrow.isNotEmpty()) {
            SectionTitle("Coming up")
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Tomorrow",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                state.upcomingTomorrow.forEach { item ->
                    Text(
                        "${item.periodLabel} · ${item.timeLabel} — ${item.title}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }

        SectionTitle("Alarms")
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .clickable { alarmDialogOpen = true }
                    .padding(vertical = 4.dp)
            ) {
                Icon(Icons.Filled.AccessAlarm, contentDescription = null)
                Text("Add alarm", fontWeight = FontWeight.SemiBold)
            }
            state.patientAlarms.filter { it.enabled }.forEach { alarm ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        formatAlarmSummary(alarm),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(onClick = { onDeleteAlarm(alarm) }) { Text("Remove") }
                }
            }
        }

        SectionTitle("Reminders")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            reminderPresets.forEach { preset ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable {
                            reminderPresetPick = preset
                            reminderDialogOpen = true
                        }
                        .padding(8.dp)
                ) {
                    Icon(Icons.Filled.NotificationsNone, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(preset, style = MaterialTheme.typography.labelLarge)
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

internal fun formatAlarmSummary(alarm: PatientAlarmEntity): String {
    val time = String.format(
        Locale.getDefault(),
        "%d:%02d",
        alarm.hour,
        alarm.minute
    )
    return when (alarm.repeatMode) {
        PatientAlarmRepeatMode.ONCE ->
            "Once · ${ReminderUiFormatter.formatEpochDate(alarm.nextTriggerAt)} · $time"
        PatientAlarmRepeatMode.DAILY -> "Daily · $time"
        PatientAlarmRepeatMode.WEEKLY -> {
            val days = ReminderUiFormatter.maskToLabel(alarm.daysOfWeekMask)
            if (days.isBlank()) "Weekly · $time" else "$days · $time"
        }
    }
}

@Composable
private fun ScheduleChunk(title: String, items: List<ScheduleSlotItem>) {
    Column {
        Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
        if (items.isEmpty()) {
            Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        } else {
            items.forEach { row ->
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(row.timeLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(row.title, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
