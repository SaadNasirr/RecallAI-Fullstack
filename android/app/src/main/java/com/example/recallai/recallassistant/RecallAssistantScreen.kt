package com.example.recallai.recallassistant

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun RecallAssistantScreen(
    onBack: () -> Unit,
    onNavigateHome: () -> Unit = {},
    onNavigateChat: () -> Unit = {},
    onNavigateFace: () -> Unit = {},
    onNavigateMemories: () -> Unit = {},
    onNavigateRecall: () -> Unit = {},
    viewModel: RecallAssistantViewModel = hiltViewModel()
) {
    if (RecallUiLayout.USE_LIVELY_RECALL_UI) {
        LivelyRecallAssistantScreen(
            onBack = onBack,
            onNavigateMemories = onNavigateMemories,
            viewModel = viewModel
        )
    } else {
        RecallAssistantScreenLegacy(
            onBack = onBack,
            onNavigateHome = onNavigateHome,
            onNavigateChat = onNavigateChat,
            onNavigateFace = onNavigateFace,
            onNavigateMemories = onNavigateMemories,
            onNavigateRecall = onNavigateRecall,
            viewModel = viewModel
        )
    }
}
