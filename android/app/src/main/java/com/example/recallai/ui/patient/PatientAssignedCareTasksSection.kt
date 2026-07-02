package com.example.recallai.ui.patient

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.recallai.ui.screens.PatientAssignedCareTask

@Composable
fun PatientAssignedCareTasksSection(
    tasks: List<PatientAssignedCareTask>,
    onDone: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (PatientCareUiLayout.USE_LIVELY_CARE_UI) {
        LivelyPatientAssignedCareTasksSection(tasks = tasks, onDone = onDone, modifier = modifier)
    } else {
        PatientAssignedCareTasksSectionLegacy(tasks = tasks, onDone = onDone, modifier = modifier)
    }
}
