package com.example.recallai.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class PatientAlarmRepeatMode {
    ONCE,
    DAILY,
    WEEKLY
}

@Entity(
    tableName = "patient_alarms",
    indices = [Index(value = ["clientId"], unique = true)]
)
data class PatientAlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** Stable id for cloud sync across devices. */
    val clientId: String = java.util.UUID.randomUUID().toString(),
    val label: String,
    val hour: Int,
    val minute: Int,
    val repeatMode: PatientAlarmRepeatMode,
    /**
     * Bits 0–6 = Mon..Sun (bit set = active). Used when [repeatMode] is [PatientAlarmRepeatMode.WEEKLY].
     */
    val daysOfWeekMask: Int = 0,
    val enabled: Boolean = true,
    /** Next scheduled wall-clock firing (epoch millis, local device zone). */
    val nextTriggerAt: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** Non-null while waiting for the patient to acknowledge the active alarm. */
    val pendingAckSinceMs: Long? = null
)

@Entity(tableName = "mood_checkins")
data class MoodCheckInEntity(
    @PrimaryKey val dayKey: String,
    val mood: String,
    val loggedAt: Long,
    val syncedAt: Long? = null
)
