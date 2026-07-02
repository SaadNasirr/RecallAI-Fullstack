package com.example.recallai.memories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.recallai.data.MemoryRepository
import com.example.recallai.data.local.MemoryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MemoriesUiState(
    val memories: List<MemoryEntity> = emptyList(),
    val isLoading: Boolean = false,
    val isSyncing: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class MemoriesViewModel @Inject constructor(
    private val repo: MemoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MemoriesUiState(isLoading = true))
    val uiState: StateFlow<MemoriesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repo.observeRecentMemories(limit = 200).collect { list ->
                _uiState.value = _uiState.value.copy(
                    memories = list,
                    isLoading = false
                )
            }
        }
        loadMemories()
    }

    fun loadMemories() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncing = true, error = null)
            val ok = repo.syncFromServer()
            _uiState.value = _uiState.value.copy(
                isSyncing = false,
                isLoading = false,
                error = if (!ok && _uiState.value.memories.isEmpty()) {
                    "Could not sync memories. Check your connection and try again."
                } else {
                    null
                }
            )
        }
    }
}
