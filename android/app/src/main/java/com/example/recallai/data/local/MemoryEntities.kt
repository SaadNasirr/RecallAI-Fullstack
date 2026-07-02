package com.example.recallai.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Local sync state for cloud-backed memories. */
enum class MemorySyncStatus {
    PENDING,
    SYNCED,
    FAILED,
    /** Pre-sync rows kept on device until uploaded or cleared on logout. */
    LEGACY
}

@Entity(
    tableName = "memories",
    indices = [Index(value = ["serverId"], unique = true)]
)
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** MongoDB _id when synced; null while pending or legacy-only. */
    val serverId: String? = null,
    /** Patient or selected-patient user id this row belongs to. */
    val userId: String? = null,
    val createdAt: Long,
    val title: String?,
    val text: String,
    val type: String,                    // "VOICE", "TEXT", "IMAGE", "CHATBOT"
    val mood: String?,                   // "happy", "stressed", etc.
    val tags: String?,                   // comma-separated tags, e.g. "family,health"
    val isImportant: Boolean = false,
    val syncStatus: MemorySyncStatus = MemorySyncStatus.LEGACY
)

@Entity(
    tableName = "memory_media",
    foreignKeys = [
        ForeignKey(
            entity = MemoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["memoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("memoryId")]
)
data class MemoryMediaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val memoryId: Long,
    val type: String,        // "audio", "image"
    val uri: String,         // content:// or file://
    val transcription: String?,
    val thumbnailUri: String?
)

enum class ReminderStatus { PENDING, COMPLETED }

enum class ReminderRepeatMode {
    /** Single fire at [datetime]. */
    NONE,
    DAILY,
    WEEKLY
}

@Entity(
    tableName = "reminders",
    indices = [Index(value = ["clientId"], unique = true)]
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** Stable id for cloud sync across devices. */
    val clientId: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val description: String? = null,
    /** Next scheduled fire (epoch millis, device default zone). */
    val datetime: Long,
    val status: ReminderStatus = ReminderStatus.PENDING,
    /** e.g. "chat" */
    val source: String = "chat",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** If true, also schedule a warning 10 minutes before. */
    val warn10Min: Boolean = true,
    /** Optional preset tag from patient dashboard: Medication, Appointment, etc. */
    val preset: String? = null,
    val repeatMode: ReminderRepeatMode = ReminderRepeatMode.NONE,
    /** Bits 0–6 = Mon..Sun when [repeatMode] is [ReminderRepeatMode.WEEKLY]. */
    val daysOfWeekMask: Int = 0
)