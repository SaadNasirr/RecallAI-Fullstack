package com.example.recallai.data

import com.example.recallai.data.local.MoodCheckInDao
import com.example.recallai.data.local.MoodCheckInEntity
import com.example.recallai.data.remote.ApiClient
import com.example.recallai.data.remote.MoodLogRequest
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

@Singleton
class MoodCheckInRepository @Inject constructor(
    private val dao: MoodCheckInDao,
    private val careRepository: CareRepository
) {

    fun observeToday(): Flow<MoodCheckInEntity?> =
        dao.observeDay(LocalDate.now().toString())

    suspend fun submitMood(mood: String) = withContext(Dispatchers.IO) {
        val dayKey = LocalDate.now().toString()
        val now = System.currentTimeMillis()
        dao.upsert(MoodCheckInEntity(dayKey = dayKey, mood = mood, loggedAt = now))
        runCatching {
            val score = when (mood) {
                "happy" -> 5
                "neutral" -> 3
                else -> 1
            }
            ApiClient.api.logMood(
                MoodLogRequest(
                    score = score,
                    note = "Daily check-in",
                    context = "patient_dashboard",
                    activities = listOf("check_in")
                )
            )
            careRepository.dispatchAlert(
                type = "mood_checkin",
                title = "Mood check-in",
                body = "Patient: $mood",
                metadata = mapOf("mood" to mood)
            )
            dao.markSynced(dayKey, System.currentTimeMillis())
        }
    }
}
