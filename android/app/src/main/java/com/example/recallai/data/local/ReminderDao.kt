package com.example.recallai.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(reminder: ReminderEntity): Long

    @Update
    suspend fun update(reminder: ReminderEntity)

    @Delete
    suspend fun delete(reminder: ReminderEntity)

    @Query("SELECT * FROM reminders WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE clientId = :clientId LIMIT 1")
    suspend fun getByClientId(clientId: String): ReminderEntity?

    @Query("SELECT * FROM reminders")
    suspend fun getAll(): List<ReminderEntity>

    @Query("DELETE FROM reminders WHERE clientId NOT IN (:clientIds)")
    suspend fun deleteNotIn(clientIds: List<String>)

    @Query("SELECT * FROM reminders ORDER BY datetime ASC")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE status = 'PENDING' AND datetime >= :fromMs ORDER BY datetime ASC")
    suspend fun getUpcomingPending(fromMs: Long): List<ReminderEntity>

    @Query("UPDATE reminders SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setStatus(id: Long, status: ReminderStatus, updatedAt: Long = System.currentTimeMillis())
}

