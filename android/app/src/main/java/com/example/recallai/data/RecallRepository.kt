package com.example.recallai.data

import com.example.recallai.data.remote.ApiClient
import com.example.recallai.data.remote.RecallChatRequest
import com.example.recallai.data.remote.RecallChatResponse
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import kotlinx.coroutines.withContext

class RecallRepository @Inject constructor(
    private val careRepository: CareRepository
) {
    private val api get() = ApiClient.api

    suspend fun recall(query: String): RecallChatResponse = withContext(Dispatchers.IO) {
        val pid =
            if (AuthManager.userRole == "caregiver") careRepository.selectedPatientId.value else null
        api.recallChat(RecallChatRequest(query = query, patientId = pid))
    }
}

