package com.example.recallai.caregiver

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import com.example.recallai.R
import com.example.recallai.ui.components.AppBackdrop
import com.example.recallai.ui.components.GlassCard
import com.example.recallai.ui.components.HeroHeaderCard
import com.example.recallai.ui.components.PrimaryActionButton
import com.example.recallai.ui.components.RecallTopBar
import com.example.recallai.ui.components.SectionTitle
import com.example.recallai.ui.components.StatPill
import com.example.recallai.ui.components.AnimatedAssistChip

@Composable
fun CaregiverRulesScreen(
    onBack: () -> Unit,
    viewModel: CaregiverRulesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    AppBackdrop() {
        androidx.compose.material3.Scaffold(
            containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.12f),
            topBar = { RecallTopBar(title = "Rules", onBack = onBack) }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                HeroHeaderCard(
                    title = "Rules",
                    subtitle = "",
                    illustrationRes = R.drawable.img_tool_caregiver
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatPill("Risk", "${state.riskThreshold.toInt()}%")
                    StatPill("Inactivity", "${state.inactivityDays.toInt()} days")
                }
                Spacer(Modifier.height(10.dp))
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    SectionTitle("Risk")
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AnimatedAssistChip("Balanced", onClick = viewModel::applyPresetBalanced)
                        AnimatedAssistChip("Strict", onClick = viewModel::applyPresetStrict)
                        AnimatedAssistChip("Relaxed", onClick = viewModel::applyPresetRelaxed)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Risk threshold", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Slider(
                        value = state.riskThreshold,
                        onValueChange = viewModel::updateRiskThreshold,
                        valueRange = 20f..100f
                    )
                    Text("Above ${state.riskThreshold.toInt()}%.")
                    Spacer(Modifier.height(10.dp))
                    Text("Idle days", fontWeight = FontWeight.SemiBold)
                    Slider(
                        value = state.inactivityDays,
                        onValueChange = viewModel::updateInactivityDays,
                        valueRange = 1f..14f
                    )
                    Text("After ${state.inactivityDays.toInt()} days.")
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Soft haptics", fontWeight = FontWeight.SemiBold)
                        Switch(
                            checked = state.reduceHaptics,
                            onCheckedChange = viewModel::updateReduceHaptics
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    PrimaryActionButton(
                        text = "Save",
                        onClick = viewModel::save,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(state.info, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

