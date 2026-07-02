package com.example.recallai.data

import com.example.recallai.data.remote.ApiClient
import com.example.recallai.data.remote.FaceAnalysisResponse
import com.example.recallai.data.remote.FaceEnrollResponse
import com.example.recallai.data.remote.FaceRecognizeResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.RequestBody
import java.io.File
import javax.inject.Inject

class FaceRepository @Inject constructor(
    private val careRepository: CareRepository
) {
    private val api get() = ApiClient.api

    private fun patientIdBody(): RequestBody? {
        val pid =
            if (AuthManager.userRole == "caregiver") careRepository.selectedPatientId.value else null
        return pid?.trim()?.takeIf { it.isNotBlank() }
            ?.toRequestBody("text/plain".toMediaType())
    }

    suspend fun analyzeFace(imageFile: File, mimeType: String, contextHint: String): FaceAnalysisResponse =
        withContext(Dispatchers.IO) {
            val imagePart = MultipartBody.Part.createFormData(
                name = "image",
                filename = imageFile.name,
                body = imageFile.asRequestBody(mimeType.toMediaType())
            )
            val hintBody = contextHint.toRequestBody("text/plain".toMediaType())
            api.analyzeFace(imagePart, hintBody, patientIdBody())
        }

    suspend fun recognizeFace(imageFile: File, mimeType: String): FaceRecognizeResponse =
        withContext(Dispatchers.IO) {
            val imagePart = MultipartBody.Part.createFormData(
                name = "image",
                filename = imageFile.name,
                body = imageFile.asRequestBody(mimeType.toMediaType())
            )
            api.recognizeFace(imagePart, patientIdBody())
        }

    suspend fun enrollFace(imageFile: File, mimeType: String, name: String): FaceEnrollResponse =
        withContext(Dispatchers.IO) {
            val imagePart = MultipartBody.Part.createFormData(
                name = "image",
                filename = imageFile.name,
                body = imageFile.asRequestBody(mimeType.toMediaType())
            )
            val nameBody = name.toRequestBody("text/plain".toMediaType())
            api.enrollFace(imagePart, nameBody, patientIdBody())
        }
}

