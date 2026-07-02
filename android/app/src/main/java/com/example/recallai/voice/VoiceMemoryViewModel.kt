package com.example.recallai.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.recallai.data.MemoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VoiceMemoryUiState(
    val text: String = "",
    val isListening: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false
)

@HiltViewModel
class VoiceMemoryViewModel @Inject constructor(
    private val memoryRepo: MemoryRepository
) : ViewModel() {

    var uiState = androidx.compose.runtime.mutableStateOf(VoiceMemoryUiState())
        private set

    fun onTextChange(newText: String) {
        uiState.value = uiState.value.copy(text = newText, error = null, saved = false)
    }

    fun setListening(listening: Boolean) {
        uiState.value = uiState.value.copy(isListening = listening, error = null, saved = false)
    }

    fun saveMemory() {
        val text = uiState.value.text.trim()
        if (text.isEmpty() || uiState.value.isSaving) return

        uiState.value = uiState.value.copy(isSaving = true, error = null)
        viewModelScope.launch {
            try {
                memoryRepo.saveTextMemory(
                    text = text,
                    title = text.take(40),
                    type = "VOICE",
                    tags = listOf("voice", "spoken")
                )
                uiState.value = uiState.value.copy(
                    isSaving = false,
                    saved = true
                )
            } catch (e: Exception) {
                uiState.value = uiState.value.copy(
                    isSaving = false,
                    error = e.message ?: "Failed to save memory"
                )
            }
        }
    }
}