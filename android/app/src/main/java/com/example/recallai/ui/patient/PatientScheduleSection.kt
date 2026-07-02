package com.example.recallai.ui.patient

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.recallai.data.local.PatientAlarmEntity
import com.example.recallai.data.local.PatientAlarmRepeatMode
import com.example.recallai.data.local.ReminderRepeatMode
import com.example.recallai.ui.screens.PatientHomeUiState

@Composable
fun PatientScheduleSection(
    state: PatientHomeUiState,
    onAddAlarm: (String, Int, Int, PatientAlarmRepeatMode, Int, Long?) -> Unit,
    onDeleteAlarm: (PatientAlarmEntity) -> Unit,
    onAddReminder: (String, String, Long, ReminderRepeatMode, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (PatientCareUiLayout.USE_LIVELY_CARE_UI) {
        LivelyPatientScheduleSection(
            state = state,
            onAddAlarm = onAddAlarm,
            onDeleteAlarm = onDeleteAlarm,
            onAddReminder = onAddReminder,
            modifier = modifier
        )
    } else {
        PatientScheduleSectionLegacy(
            state = state,
            onAddAlarm = onAddAlarm,
            onDeleteAlarm = onDeleteAlarm,
            onAddReminder = onAddReminder,
            modifier = modifier
        )
    }
}
