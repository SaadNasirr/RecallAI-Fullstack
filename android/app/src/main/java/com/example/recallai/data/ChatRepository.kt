package com.example.recallai.data

import com.example.recallai.data.remote.ApiClient
import com.example.recallai.data.remote.ChatMessageRequest
import com.example.recallai.data.remote.ChatMessageResponse
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

@Singleton
class ChatRepository @Inject constructor(
    private val careRepository: CareRepository
) {

    private val api get() = ApiClient.api

    private var sessionId: String? = null
    private var sessionPatientKey: String? = null

    private companion object {
        val RETRY_HTTP_CODES = setOf(502, 503, 504, 520, 521, 522, 524, 529, 530)
    }

    private fun effectivePatientKey(): String {
        val pid =
            if (AuthManager.userRole == "caregiver") careRepository.selectedPatientId.value else null
        return pid ?: "__self__"
    }

    private fun patientQueryParam(): String? =
        if (AuthManager.userRole == "caregiver") careRepository.selectedPatientId.value else null

    private fun ensurePatientContext() {
        val key = effectivePatientKey()
        if (sessionPatientKey != key) {
            sessionId = null
            sessionPatientKey = key
        }
    }

    fun currentSessionId(): String? {
        ensurePatientContext()
        return sessionId
    }

    fun adoptSession(id: String) {
        ensurePatientContext()
        sessionId = id
    }

    private suspend fun <T> retryOnTunnelOrOriginErrors(block: suspend () -> T): T {
        var last: Exception? = null
        repeat(3) { attempt ->
            try {
                return block()
            } catch (e: HttpException) {
                last = e
                val code = e.code()
                if (code in RETRY_HTTP_CODES && attempt < 2) {
                    delay(400L * (attempt + 1))
                } else {
                    throw e
                }
            } catch (e: IOException) {
                last = e
                if (attempt < 2) {
                    delay(400L * (attempt + 1))
                } else {
                    throw e
                }
            }
        }
        throw last ?: IllegalStateException("Chat request failed after retries.")
    }

    suspend fun ensureSession(): String = withContext(Dispatchers.IO) {
        ensurePatientContext()
        sessionId ?: retryOnTunnelOrOriginErrors {
            api.createChatSession(patientQueryParam()).sessionId
        }.also { sessionId = it }
    }

    suspend fun sendMessage(text: String): ChatMessageResponse =
        withContext(Dispatchers.IO) {
            val id = ensureSession()
            val pid = patientQueryParam()
            retryOnTunnelOrOriginErrors {
                api.sendChatMessage(id, ChatMessageRequest(message = text), pid)
            }
        }
}
