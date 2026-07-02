package com.example.recallai.objectlocator

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.recallai.data.MemoryRepository
import com.example.recallai.data.ObjectLocatorRepository
import com.example.recallai.data.local.MemoryEntity
import com.example.recallai.data.local.MemoryMediaEntity
import com.example.recallai.data.remote.LocatorResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import com.example.recallai.memories.MemoryNavigation
import com.example.recallai.memories.MemoryOpenCoordinator
import com.example.recallai.memories.MemoryOpenPayload
import javax.inject.Inject

data class SavedObjectInsight(
    val memoryId: Long,
    val headline: String,
    val detail: String,
    val imagePath: String?,
    val createdAt: Long
)

data class ObjectLocatorUiState(
    val query: String = "",
    val textQuery: String? = null,
    val responseText: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    val lastSavedObject: SavedObjectInsight? = null,
    val recentObjectSaves: List<SavedObjectInsight> = emptyList()
)

@HiltViewModel
class ObjectLocatorViewModel @Inject constructor(
    private val locatorRepository: ObjectLocatorRepository,
    private val memoryRepo: MemoryRepository,
    private val memoryOpenCoordinator: MemoryOpenCoordinator,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ObjectLocatorUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch { refreshMemoryInsights() }
    }

    fun applyPendingMemoryOpen() {
        val payload = memoryOpenCoordinator.consume() ?: return
        viewModelScope.launch { openFromMemory(payload) }
    }

    private suspend fun openFromMemory(payload: MemoryOpenPayload) {
        val query = MemoryNavigation.parseObjectQuery(payload.text)
            ?: payload.title?.trim()?.takeIf { it.isNotBlank() }
            ?: ""
        val result = MemoryNavigation.parseObjectResult(payload.text)
        val imagePath = if (payload.memoryId > 0L) {
            memoryRepo.getLatestImageUriForMemory(payload.memoryId)
        } else {
            null
        }
        _uiState.value = _uiState.value.copy(
            query = query.ifBlank { payload.contextLabel() },
            textQuery = query.takeIf { it.isNotBlank() },
            responseText = result.takeIf { it.isNotBlank() },
            error = null,
            info = "Opened: ${payload.contextLabel()}",
            lastSavedObject = if (payload.memoryId > 0L) {
                SavedObjectInsight(
                    memoryId = payload.memoryId,
                    headline = payload.title ?: "Object memory",
                    detail = result,
                    imagePath = imagePath,
                    createdAt = payload.createdAt
                )
            } else {
                _uiState.value.lastSavedObject
            }
        )
    }

    private suspend fun refreshMemoryInsights() {
        val rows = memoryRepo.getRecentObjectDetectionMemories(8)
        val mapped = rows.mapNotNull { mapEntityToInsight(it) }
        _uiState.value = _uiState.value.copy(
            lastSavedObject = mapped.firstOrNull(),
            recentObjectSaves = mapped.drop(1)
        )
    }

    private suspend fun mapEntityToInsight(m: MemoryEntity): SavedObjectInsight? {
        val img = memoryRepo.getLatestImageUriForMemory(m.id)
        val headline = m.title?.trim()?.takeIf { it.isNotBlank() } ?: "Object insight"
        val detail = summarizeObjectMemoryText(m.text)
        return SavedObjectInsight(m.id, headline, detail, img, m.createdAt)
    }

    private fun summarizeObjectMemoryText(full: String): String {
        val after = full.substringAfter("Result:\n", "")
        val body = if (after.isNotBlank()) after else full
        return body.lineSequence().firstOrNull()?.trim()?.take(280) ?: full.take(280)
    }

    fun onQueryChange(newQuery: String) {
        _uiState.value = _uiState.value.copy(query = newQuery, error = null, info = null)
    }

    fun analyzeImage(imageFile: File, mimeType: String) {
        val query = _uiState.value.query
        if (_uiState.value.isLoading) return

        _uiState.value = _uiState.value.copy(
            isLoading = true,
            error = null,
            info = null,
            textQuery = null,
            responseText = null
        )

        viewModelScope.launch {
            try {
                if (!imageFile.exists() || imageFile.length() < 32L) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Image is missing or not fully saved. Take or pick the photo again."
                    )
                    return@launch
                }
                val imagePart = MultipartBody.Part.createFormData(
                    name = "image",
                    filename = imageFile.name,
                    body = imageFile.asRequestBody(
                        (mimeType.ifBlank { "image/jpeg" }).toMediaTypeOrNull()
                    )
                )

                val queryBody = query.trim().toRequestBody("text/plain".toMediaTypeOrNull())

                val resp = locatorRepository.analyzeObject(
                    image = imagePart,
                    query = queryBody
                )

                saveLocatorAsMemory(resp = resp, originalQuery = query.trim(), imageFile = imageFile)

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    textQuery = resp.text_query,
                    responseText = resp.response
                )
                refreshMemoryInsights()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = humanizeLocatorError(e)
                )
            }
        }
    }

    private suspend fun saveLocatorAsMemory(resp: LocatorResponse, originalQuery: String, imageFile: File?) {
        val memoryText = buildString {
            append("Object Detection Query: ")
            append(if (originalQuery.isBlank()) "<none>" else originalQuery)
            append("\n\nBackend Text Query: ")
            append(resp.text_query)
            append("\n\nResult:\n")
            append(resp.response)
        }

        val title = resp.text_query.take(50).ifBlank { "Object scan" }

        if (imageFile != null && imageFile.exists()) {
            val dir = File(appContext.filesDir, "memory_images").apply { mkdirs() }
            val dest = File(dir, "obj_${System.currentTimeMillis()}.jpg")
            runCatching { imageFile.copyTo(dest, overwrite = true) }
            val media = listOf(
                MemoryMediaEntity(
                    memoryId = 0L,
                    type = "image",
                    uri = dest.absolutePath,
                    transcription = null,
                    thumbnailUri = null
                )
            )
            memoryRepo.saveMemoryWithMedia(
                text = memoryText,
                media = media,
                title = title,
                type = "OBJECT_DETECTION",
                tags = listOf("vision", "object", "locator")
            )
        } else {
            memoryRepo.saveTextMemory(
                text = memoryText,
                title = title,
                type = "OBJECT_DETECTION",
                tags = listOf("vision", "object", "locator")
            )
        }
    }

    fun saveCurrentResultAsImportant() {
        val textQuery = _uiState.value.textQuery.orEmpty()
        val response = _uiState.value.responseText.orEmpty()
        if (response.isBlank()) {
            _uiState.value = _uiState.value.copy(info = "No object result to save yet.")
            return
        }
        viewModelScope.launch {
            try {
                val text = buildString {
                    append("Object Detection Important Note")
                    if (textQuery.isNotBlank()) append("\nQuery: $textQuery")
                    append("\n\nResult:\n$response")
                }
                memoryRepo.saveTextMemory(
                    text = text,
                    title = "Important object insight",
                    type = "OBJECT_IMPORTANT",
                    tags = listOf("vision", "object", "important")
                )
                _uiState.value = _uiState.value.copy(info = "Saved as important memory.")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "Failed to save important memory")
            }
        }
    }
}

private fun humanizeLocatorError(e: Throwable): String = when (e) {
    is HttpException -> {
        val code = e.code()
        val rawBody = runCatching { e.response()?.errorBody()?.string()?.trim().orEmpty() }.getOrNull().orEmpty()
        val fromJson = runCatching {
            if (rawBody.startsWith("{")) JSONObject(rawBody).optString("error") else ""
        }.getOrNull().orEmpty().trim()
        val snippet = fromJson.takeIf { it.isNotBlank() } ?: rawBody.ifBlank { e.message.orEmpty() }
        when (code) {
            401 -> "Session expired. Please sign in again."
            502, 503, 504 -> "The object finder service is temporarily unavailable. Try again in a moment."
            in 500..599 -> when {
                snippet.contains("decommissioned", ignoreCase = true) ||
                    snippet.contains("no longer supported", ignoreCase = true) ->
                    "Groq retired this vision model. Update the server’s OBJECT_VISION_MODEL (try meta-llama/llama-4-scout-17b-16e-instruct) and restart the backend."
                snippet.contains("model", ignoreCase = true) &&
                    (
                        snippet.contains("does not exist", ignoreCase = true) ||
                            snippet.contains("model_not_found", ignoreCase = true) ||
                            snippet.contains("not available", ignoreCase = true)
                        ) ->
                    "The vision model on the server is not available. Set OBJECT_VISION_MODEL to a current Groq vision model (for example meta-llama/llama-4-scout-17b-16e-instruct)."
                snippet.contains("invalid api key", ignoreCase = true) ||
                    snippet.contains("incorrect api key", ignoreCase = true) ->
                    "Vision service authentication failed on the server. Check GROQ_API_KEY."
                else -> "We couldn’t complete this scan. Please try again in a moment."
            }
            in 400..499 -> when (code) {
                403 -> snippet.ifBlank { "Not allowed. If you are a caregiver, select a patient first." }
                400 -> snippet.ifBlank { "Missing image or invalid request—take the photo again and add a short question." }
                else -> snippet.ifBlank { "Request failed ($code)." }
            }
            else -> snippet.ifBlank { "Request failed ($code)." }
        }
    }
    is IOException ->
        e.message?.takeIf { it.isNotBlank() }
            ?: "Network error. Check your connection and that the backend is running."
    else -> e.message?.takeIf { it.isNotBlank() } ?: "Couldn’t analyze the image."
}
