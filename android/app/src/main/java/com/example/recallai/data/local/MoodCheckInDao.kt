package com.example.recallai.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MoodCheckInDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: MoodCheckInEntity)

    @Query("SELECT * FROM mood_checkins WHERE dayKey = :dayKey LIMIT 1")
    fun observeDay(dayKey: String): Flow<MoodCheckInEntity?>

    @Query("SELECT * FROM mood_checkins WHERE dayKey = :dayKey LIMIT 1")
    suspend fun getDay(dayKey: String): MoodCheckInEntity?

    @Query("UPDATE mood_checkins SET syncedAt = :syncedAt WHERE dayKey = :dayKey")
    suspend fun markSynced(dayKey: String, syncedAt: Long)
}
