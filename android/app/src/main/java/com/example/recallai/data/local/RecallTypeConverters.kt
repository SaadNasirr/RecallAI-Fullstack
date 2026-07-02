package com.example.recallai.data.local

import androidx.room.TypeConverter

class RecallTypeConverters {
    @TypeConverter
    fun reminderStatusToString(value: ReminderStatus): String = value.name

    @TypeConverter
    fun reminderStatusFromString(value: String): ReminderStatus =
        runCatching { ReminderStatus.valueOf(value) }.getOrDefault(ReminderStatus.PENDING)

    @TypeConverter
    fun patientAlarmRepeatToString(value: PatientAlarmRepeatMode): String = value.name

    @TypeConverter
    fun patientAlarmRepeatFromString(value: String): PatientAlarmRepeatMode =
        runCatching { PatientAlarmRepeatMode.valueOf(value) }
            .getOrDefault(PatientAlarmRepeatMode.ONCE)

    @TypeConverter
    fun reminderRepeatModeToString(value: ReminderRepeatMode): String = value.name

    @TypeConverter
    fun reminderRepeatModeFromString(value: String): ReminderRepeatMode =
        runCatching { ReminderRepeatMode.valueOf(value) }.getOrDefault(ReminderRepeatMode.NONE)

    @TypeConverter
    fun memorySyncStatusToString(value: MemorySyncStatus): String = value.name

    @TypeConverter
    fun memorySyncStatusFromString(value: String): MemorySyncStatus =
        runCatching { MemorySyncStatus.valueOf(value) }.getOrDefault(MemorySyncStatus.LEGACY)
}

