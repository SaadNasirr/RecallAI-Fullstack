package com.example.recallai.privacy

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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.example.recallai.data.CareToolkitRepository
import com.example.recallai.data.ConsentSettings
import com.example.recallai.ui.components.AppBackdrop
import com.example.recallai.ui.components.GlassCard
import com.example.recallai.ui.components.HeroHeaderCard
import com.example.recallai.ui.components.QuickSwitchRow
import com.example.recallai.ui.components.RecallTopBar
import com.example.recallai.ui.components.StatPill
import com.example.recallai.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope

data class PrivacyUiState(
    val settings: ConsentSettings = ConsentSettings(),
    val info: String? = null
)

@HiltViewModel
class PrivacyControlViewModel @Inject constructor(
    private val toolkitRepository: CareToolkitRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(PrivacyUiState(settings = toolkitRepository.getConsentSettings()))
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            toolkitRepository.syncPatientToolkitFromServer()
            _uiState.value = _uiState.value.copy(settings = toolkitRepository.getConsentSettings())
        }
    }

    fun update(settings: ConsentSettings) {
        viewModelScope.launch {
            toolkitRepository.saveConsentSettings(settings)
            _uiState.value = _uiState.value.copy(
                settings = toolkitRepository.getConsentSettings(),
                info = "Privacy preferences updated."
            )
        }
    }
}

@Composable
fun PrivacyControlScreen(
    onBack: () -> Unit,
    onNavigateHome: () -> Unit = {},
    onNavigateChat: () -> Unit = {},
    onNavigateFace: () -> Unit = {},
    onNavigateMemories: () -> Unit = {},
    onNavigateRecall: () -> Unit = {},
    viewModel: PrivacyControlViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val s = state.settings
    AppBackdrop {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.12f),
            topBar = { RecallTopBar(title = "Privacy", onBack = onBack) }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(14.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HeroHeaderCard(
                    title = "Privacy",
                    subtitle = "",
                    illustrationRes = R.drawable.img_tool_privacy
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatPill("Share", if (s.shareWithCaregiver) "On" else "Off")
                    StatPill("Location", if (s.allowLocationSharing) "On" else "Off")
                    StatPill("Voice", if (s.allowVoiceStorage) "On" else "Off")
                    StatPill("Photo", if (s.allowPhotoStorage) "On" else "Off")
                }
                QuickSwitchRow(
                    onHome = onNavigateHome,
                    onChat = onNavigateChat,
                    onFace = onNavigateFace,
                    onMemories = onNavigateMemories,
                    onRecall = onNavigateRecall
                )
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    PrivacyToggle("Share updates with caregiver", s.shareWithCaregiver) {
                        viewModel.update(s.copy(shareWithCaregiver = it))
                    }
                    PrivacyToggle("Allow location sharing", s.allowLocationSharing) {
                        viewModel.update(s.copy(allowLocationSharing = it))
                    }
                    PrivacyToggle("Allow voice storage", s.allowVoiceStorage) {
                        viewModel.update(s.copy(allowVoiceStorage = it))
                    }
                    PrivacyToggle("Allow photo storage", s.allowPhotoStorage) {
                        viewModel.update(s.copy(allowPhotoStorage = it))
                    }
                    if (!state.info.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(state.info ?: "", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivacyToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
    Spacer(Modifier.height(8.dp))
}
