package com.example.recallai.data

import com.example.recallai.data.remote.ApiClient
import com.example.recallai.data.remote.LocatorResponse
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ObjectLocatorRepository @Inject constructor(
    private val careRepository: CareRepository
) {
    private val api get() = ApiClient.api

    private fun patientIdBody(): RequestBody? {
        val pid =
            if (AuthManager.userRole == "caregiver") careRepository.selectedPatientId.value else null
        return pid?.trim()?.takeIf { it.isNotBlank() }
            ?.toRequestBody("text/plain".toMediaType())
    }

    suspend fun analyzeObject(
        image: MultipartBody.Part,
        query: RequestBody
    ): LocatorResponse = withContext(Dispatchers.IO) {
        api.analyzeObjectLocator(image = image, query = query, patientId = patientIdBody())
    }

    suspend fun analyzeObjectWithAudio(
        image: MultipartBody.Part,
        audio: MultipartBody.Part,
        query: RequestBody
    ): LocatorResponse = withContext(Dispatchers.IO) {
        api.analyzeObjectLocatorWithAudio(
            image = image,
            audio = audio,
            query = query,
            patientId = patientIdBody()
        )
    }
}

