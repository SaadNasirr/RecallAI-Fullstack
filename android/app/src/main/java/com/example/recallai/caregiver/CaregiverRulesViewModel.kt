package com.example.recallai.caregiver

import androidx.lifecycle.ViewModel
import com.example.recallai.data.CaregiverRules
import com.example.recallai.data.HapticsConfig
import com.example.recallai.data.CaregiverRulesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class CaregiverRulesUiState(
    val riskThreshold: Float = 60f,
    val inactivityDays: Float = 3f,
    val reduceHaptics: Boolean = false,
    val info: String = "Adjust rules and save."
)

@HiltViewModel
class CaregiverRulesViewModel @Inject constructor(
    private val rulesRepository: CaregiverRulesRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(CaregiverRulesUiState())
    val uiState = _uiState.asStateFlow()

    init {
        val saved = rulesRepository.getRules()
        _uiState.value = _uiState.value.copy(
            riskThreshold = saved.riskThresholdPercent.toFloat(),
            inactivityDays = saved.inactivityDays.toFloat(),
            reduceHaptics = saved.reduceHaptics,
            info = "Loaded saved caregiver rules."
        )
        HapticsConfig.reduceHaptics = saved.reduceHaptics
    }

    fun updateRiskThreshold(value: Float) {
        _uiState.value = _uiState.value.copy(riskThreshold = value)
    }

    fun updateInactivityDays(value: Float) {
        _uiState.value = _uiState.value.copy(inactivityDays = value)
    }

    fun updateReduceHaptics(value: Boolean) {
        _uiState.value = _uiState.value.copy(reduceHaptics = value)
        HapticsConfig.reduceHaptics = value
    }

    fun save() {
        val current = _uiState.value
        rulesRepository.saveRules(
            CaregiverRules(
                riskThresholdPercent = current.riskThreshold.toInt(),
                inactivityDays = current.inactivityDays.toInt(),
                reduceHaptics = current.reduceHaptics
            )
        )
        HapticsConfig.reduceHaptics = current.reduceHaptics
        _uiState.value = current.copy(info = "Rules saved successfully.")
    }

    fun applyPresetBalanced() {
        _uiState.value = _uiState.value.copy(
            riskThreshold = 60f,
            inactivityDays = 3f,
            info = "Balanced preset applied. Save to confirm."
        )
    }

    fun applyPresetStrict() {
        _uiState.value = _uiState.value.copy(
            riskThreshold = 45f,
            inactivityDays = 2f,
            info = "Strict preset applied. Save to confirm."
        )
    }

    fun applyPresetRelaxed() {
        _uiState.value = _uiState.value.copy(
            riskThreshold = 75f,
            inactivityDays = 5f,
            info = "Relaxed preset applied. Save to confirm."
        )
    }
}

