package com.example.recallai.demo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.recallai.R
import com.example.recallai.ui.components.AppBackdrop
import com.example.recallai.ui.components.AnimatedAssistChip
import com.example.recallai.ui.components.GlassCard
import com.example.recallai.ui.components.HeroHeaderCard
import com.example.recallai.ui.components.PrimaryActionButton
import com.example.recallai.ui.components.QuickSwitchRow
import com.example.recallai.ui.components.RecallTopBar
import com.example.recallai.ui.components.SectionTitle
import com.example.recallai.ui.components.StatPill

@Composable
fun DemoModeScreen(
    onBack: () -> Unit,
    onNavigateHome: () -> Unit = {},
    onNavigateChat: () -> Unit = {},
    onNavigateFace: () -> Unit = {},
    onNavigateMemories: () -> Unit = {},
    onNavigateRecall: () -> Unit = {},
    viewModel: DemoModeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var triggerCount by remember { mutableIntStateOf(0) }
    AppBackdrop {
        androidx.compose.material3.Scaffold(
            containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.12f),
            topBar = { RecallTopBar(title = "Demo", onBack = onBack) }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(14.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                HeroHeaderCard(
                    title = "Demo",
                    subtitle = "",
                    illustrationRes = R.drawable.img_tool_chatbot
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatPill("Mode", "Demo")
                    StatPill("Triggers", triggerCount.toString())
                    StatPill("Status", "Ready")
                }
                Spacer(Modifier.height(10.dp))
                QuickSwitchRow(
                    onHome = onNavigateHome,
                    onChat = onNavigateChat,
                    onFace = onNavigateFace,
                    onMemories = onNavigateMemories,
                    onRecall = onNavigateRecall
                )
                Spacer(Modifier.height(10.dp))
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    SectionTitle("Simulate")
                    Spacer(Modifier.height(6.dp))
                    PrimaryActionButton(
                        text = "Geofence",
                        onClick = {
                            triggerCount++
                            viewModel.simulateGeofenceAlert()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    PrimaryActionButton(
                        text = "Med miss",
                        onClick = {
                            triggerCount++
                            viewModel.simulateMedicationMiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    PrimaryActionButton(
                        text = "Face",
                        onClick = {
                            triggerCount++
                            viewModel.simulateFaceInsight()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AnimatedAssistChip(
                            label = "Run all",
                            onClick = {
                                triggerCount += 3
                                viewModel.simulateGeofenceAlert()
                                viewModel.simulateMedicationMiss()
                                viewModel.simulateFaceInsight()
                            }
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(state.info, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

