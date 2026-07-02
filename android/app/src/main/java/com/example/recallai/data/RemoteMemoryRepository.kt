package com.example.recallai.data

import com.example.recallai.data.remote.AddMemoryRequest
import com.example.recallai.data.remote.AddMemoryResponse
import com.example.recallai.data.remote.ApiClient
import com.example.recallai.data.remote.RemoteMemoryDto
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import kotlinx.coroutines.withContext

class RemoteMemoryRepository @Inject constructor(
    private val careRepository: CareRepository
) {
    private val api get() = ApiClient.api

    suspend fun saveMemory(text: String): AddMemoryResponse = withContext(Dispatchers.IO) {
        val pid =
            if (AuthManager.userRole == "caregiver") careRepository.selectedPatientId.value else null
        api.addRemoteMemory(AddMemoryRequest(text = text, patientId = pid))
    }

    suspend fun getMemories(): List<RemoteMemoryDto> = withContext(Dispatchers.IO) {
        val pid =
            if (AuthManager.userRole == "caregiver") careRepository.selectedPatientId.value else null
        api.getRemoteMemories(patientId = pid)
    }
}

