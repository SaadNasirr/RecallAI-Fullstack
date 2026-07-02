package com.example.recallai.data

import com.example.recallai.data.remote.ApiClient
import com.example.recallai.data.remote.CreateGeofenceRequest
import com.example.recallai.data.remote.GeofenceEventRequest
import com.example.recallai.data.remote.GeofenceEventResponse
import com.example.recallai.data.remote.GeofenceResponse
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class GeofenceRepository @Inject constructor() {

    private val api get() = ApiClient.api

    suspend fun createZone(req: CreateGeofenceRequest): Result<GeofenceResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val res = api.createGeofence(req)
                if (!res.isSuccessful || res.body() == null) {
                    throw IllegalStateException(res.errorBody()?.string() ?: "create failed")
                }
                res.body()!!
            }
        }

    suspend fun listZones(patientId: String?): Result<List<GeofenceResponse>> =
        withContext(Dispatchers.IO) {
            runCatching { api.getMyGeofenceZones(patientId) }
        }

    suspend fun deleteZone(id: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val res = api.deleteGeofence(id)
                if (!res.isSuccessful) {
                    throw IllegalStateException(res.errorBody()?.string() ?: "delete failed")
                }
            }
        }

    suspend fun toggleZone(id: String): Result<GeofenceResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val res = api.toggleGeofence(id)
                if (!res.isSuccessful || res.body() == null) {
                    throw IllegalStateException(res.errorBody()?.string() ?: "toggle failed")
                }
                res.body()!!
            }
        }

    suspend fun reportEvent(body: GeofenceEventRequest): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val res = api.reportGeofenceEvent(body)
                if (!res.isSuccessful) {
                    throw IllegalStateException(res.errorBody()?.string() ?: "event failed")
                }
            }
        }

    suspend fun eventsForPatient(patientId: String): Result<List<GeofenceEventResponse>> =
        withContext(Dispatchers.IO) {
            runCatching { api.getGeofenceEvents(patientId) }
        }
}
