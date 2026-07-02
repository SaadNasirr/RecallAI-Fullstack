package com.example.recallai.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PatientAlarmDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alarm: PatientAlarmEntity): Long

    @Update
    suspend fun update(alarm: PatientAlarmEntity)

    @Delete
    suspend fun delete(alarm: PatientAlarmEntity)

    @Query("SELECT * FROM patient_alarms WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PatientAlarmEntity?

    @Query("SELECT * FROM patient_alarms WHERE clientId = :clientId LIMIT 1")
    suspend fun getByClientId(clientId: String): PatientAlarmEntity?

    @Query("SELECT * FROM patient_alarms")
    suspend fun getAll(): List<PatientAlarmEntity>

    @Query("DELETE FROM patient_alarms WHERE clientId NOT IN (:clientIds)")
    suspend fun deleteNotIn(clientIds: List<String>)

    @Query("SELECT * FROM patient_alarms ORDER BY hour, minute")
    fun observeAll(): Flow<List<PatientAlarmEntity>>

    @Query("SELECT * FROM patient_alarms WHERE enabled = 1 AND nextTriggerAt >= :fromMs ORDER BY nextTriggerAt ASC")
    suspend fun getScheduledFrom(fromMs: Long): List<PatientAlarmEntity>

    @Query("SELECT * FROM patient_alarms WHERE enabled = 1 ORDER BY nextTriggerAt ASC")
    suspend fun getEnabled(): List<PatientAlarmEntity>

    @Query("UPDATE patient_alarms SET pendingAckSinceMs = :ms, updatedAt = :u WHERE id = :id")
    suspend fun setPendingAck(id: Long, ms: Long?, u: Long = System.currentTimeMillis())
}
