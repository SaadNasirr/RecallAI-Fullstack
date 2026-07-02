package com.example.recallai.ui.patient

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.recallai.ui.components.AnimatedAssistChip
import com.example.recallai.ui.components.GlassCard
import com.example.recallai.ui.screens.PatientAssignedCareTask

/** Frozen copy before lively care UI. Restored when [PatientCareUiLayout.USE_LIVELY_CARE_UI] is false. */
@Composable
fun PatientAssignedCareTasksSectionLegacy(
    tasks: List<PatientAssignedCareTask>,
    onDone: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Care tasks", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (tasks.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Assignment,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("No tasks assigned yet", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                tasks.forEach { t ->
                    val border = when (t.priority.uppercase()) {
                        "HIGH" -> Color(0xFFE53935)
                        "LOW" -> Color(0xFF43A047)
                        else -> Color(0xFFFFA000)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(start = 10.dp, top = 10.dp, bottom = 10.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .width(4.dp)
                                .height(48.dp)
                                .background(border, RoundedCornerShape(2.dp))
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.title, fontWeight = FontWeight.SemiBold)
                            val pr = t.priority.uppercase()
                            Text(
                                pr,
                                style = MaterialTheme.typography.labelSmall,
                                color = border,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${t.caregiverName} · ${t.dueLabel}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!t.description.isNullOrBlank()) {
                                Text(t.description!!, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (!t.isDone) {
                            AnimatedAssistChip(label = "Done", onClick = { onDone(t.id) })
                        } else {
                            Text("Done", style = MaterialTheme.typography.labelMedium, color = Color(0xFF2E7D32))
                        }
                    }
                }
            }
        }
    }
}
