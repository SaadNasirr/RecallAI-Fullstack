package com.example.recallai.recallassistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.recallai.data.MemoryRepository
import com.example.recallai.data.RecallRepository
import com.example.recallai.data.local.MemoryEntity
import com.example.recallai.memories.MemoryNavigation
import com.example.recallai.memories.MemoryOpenCoordinator
import com.example.recallai.memories.MemoryOpenPayload
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SavedRecallInsight(
    val memoryId: Long,
    val headline: String,
    val detail: String,
    val createdAt: Long,
    /** Parsed question (same as headline when stored title-only). */
    val question: String = headline,
    /** Parsed answer snippet. */
    val answer: String = detail
)

data class RecallAssistantUiState(
    val query: String = "",
    val responseText: String? = null,
    val sources: List<String> = emptyList(),
    val isLoading: Boolean = false,
    /** UX phases while the single network recall runs (backend still one shot). */
    val loadingPhase: RecallLoadingPhase? = null,
    val error: String? = null,
    val info: String? = null,
    val lastSavedRecall: SavedRecallInsight? = null,
    val recentRecalls: List<SavedRecallInsight> = emptyList()
)

enum class RecallLoadingPhase {
    SEARCHING_MEMORIES,
    GENERATING_ANSWER
}

@HiltViewModel
class RecallAssistantViewModel @Inject constructor(
    private val recallRepository: RecallRepository,
    private val memoryRepo: MemoryRepository,
    private val memoryOpenCoordinator: MemoryOpenCoordinator
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecallAssistantUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch { refreshRecallInsights() }
    }

    fun applyPendingMemoryOpen() {
        memoryOpenCoordinator.consume()?.let { openFromMemory(it) }
    }

    private fun openFromMemory(payload: MemoryOpenPayload) {
        val query = MemoryNavigation.parseRecallQuery(payload.text)
            ?: payload.title?.trim()?.takeIf { it.isNotBlank() }
            ?: payload.text.lineSequence().firstOrNull()?.trim().orEmpty()
        val answer = MemoryNavigation.parseRecallAnswer(payload.text)
        _uiState.update {
            it.copy(
                query = query,
                responseText = answer.takeIf { a -> a.isNotBlank() && a != query },
                sources = emptyList(),
                error = null,
                info = "Opened: ${payload.contextLabel()}",
                isLoading = false,
                loadingPhase = null
            )
        }
    }

    private suspend fun refreshRecallInsights() {
        val rows = memoryRepo.getRecentRecallMemories(8)
        val mapped = rows.map(::mapEntityToInsight)
        _uiState.value = _uiState.value.copy(
            lastSavedRecall = mapped.firstOrNull(),
            recentRecalls = mapped.drop(1)
        )
    }

    private fun mapEntityToInsight(m: MemoryEntity): SavedRecallInsight {
        val full = m.text
        val parsedQuery = parseRecallQuery(full)
        val headline = m.title?.trim()?.takeIf { it.isNotBlank() } ?: "Semantic recall"
        val question = parsedQuery ?: headline
        val answer = summarizeRecallText(full)
        return SavedRecallInsight(
            memoryId = m.id,
            headline = question.take(80),
            detail = answer,
            createdAt = m.createdAt,
            question = question,
            answer = answer
        )
    }

    private fun parseRecallQuery(full: String): String? {
        val marker = "Recall Query:"
        val idx = full.indexOf(marker, ignoreCase = true)
        if (idx < 0) return null
        val after = full.substring(idx + marker.length).trimStart()
        val end = after.indexOf("\n\nAnswer")
        val end2 = after.indexOf("\nAnswer")
        val cut = when {
            end >= 0 -> after.substring(0, end)
            end2 >= 0 -> after.substring(0, end2)
            else -> after.substringBefore("\n\n").ifBlank { after.substringBefore("\n") }
        }
        return cut.trim().takeIf { it.isNotBlank() }
    }

    private fun summarizeRecallText(full: String): String {
        val afterAnswer = full.substringAfter("Answer:\n", "").substringAfter("Answer:", "")
        var body = if (afterAnswer.isNotBlank()) afterAnswer else full
        body = body.substringBefore("\n\nSources").substringBefore("\nSources:").trim()
        return body.lineSequence().firstOrNull()?.trim()?.take(280) ?: full.take(280)
    }

    fun onQueryChange(newQuery: String) {
        _uiState.value = _uiState.value.copy(query = newQuery, error = null, info = null)
    }

    fun submit() {
        val query = _uiState.value.query.trim()
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Please enter a recall question.")
            return
        }

        if (_uiState.value.isLoading) return

        _uiState.value = _uiState.value.copy(
            isLoading = true,
            loadingPhase = RecallLoadingPhase.SEARCHING_MEMORIES,
            error = null,
            info = null,
            responseText = null,
            sources = emptyList()
        )

        viewModelScope.launch {
            val phaseAdvance = launch {
                delay(1_200)
                _uiState.update { s ->
                    if (s.isLoading) s.copy(loadingPhase = RecallLoadingPhase.GENERATING_ANSWER) else s
                }
            }
            try {
                val resp = recallRepository.recall(query = query)

                val rawAnswer = resp.response
                val looksLikeApiKeyFailure =
                    rawAnswer.contains("invalid_api_key", ignoreCase = true) ||
                        rawAnswer.contains("Invalid API Key", ignoreCase = true) ||
                        rawAnswer.contains("Error generating response", ignoreCase = true) ||
                        rawAnswer.contains("401")

                if (looksLikeApiKeyFailure) {
                    phaseAdvance.cancel()
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        loadingPhase = null,
                        error = "Recall failed on the server (invalid/missing AI API key). Please check backend GROQ_API_KEY.",
                        responseText = null,
                        sources = resp.sources
                    )
                    return@launch
                }

                val memoryText = buildString {
                    append("Recall Query: ")
                    append(query)
                    append("\n\nAnswer:\n")
                    append(rawAnswer)
                    if (resp.sources.isNotEmpty()) {
                        append("\n\nSources:\n")
                        append(resp.sources.joinToString("\n"))
                    }
                }

                memoryRepo.saveTextMemory(
                    text = memoryText,
                    title = query.take(50),
                    type = "RECALL_ASSISTANT",
                    tags = listOf("recall", "assistant")
                )

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    loadingPhase = null,
                    responseText = resp.response,
                    sources = resp.sources
                )
                refreshRecallInsights()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    loadingPhase = null,
                    error = e.message ?: "Failed to run recall"
                )
            } finally {
                phaseAdvance.cancel()
            }
        }
    }

    fun saveCurrentAnswerAsNote() {
        val query = _uiState.value.query.trim()
        val answer = _uiState.value.responseText?.trim().orEmpty()
        if (answer.isBlank()) {
            _uiState.value = _uiState.value.copy(info = "No recall answer to save yet.")
            return
        }
        viewModelScope.launch {
            try {
                val text = buildString {
                    append("Recall Note")
                    if (query.isNotBlank()) append("\nQuery: $query")
                    append("\n\n$answer")
                }
                memoryRepo.saveTextMemory(
                    text = text,
                    title = if (query.isNotBlank()) query.take(50) else "Recall note",
                    type = "RECALL_NOTE",
                    tags = listOf("recall", "note", "saved")
                )
                _uiState.value = _uiState.value.copy(info = "Saved recall answer to memories.")
                refreshRecallInsights()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "Failed to save recall note")
            }
        }
    }
}
