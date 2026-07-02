package com.example.recallai.reminders

internal object PatientAlarmNotifications {
    fun notificationId(alarmId: Long): Int {
        val base = (alarmId xor (alarmId ushr 32)).toInt() and 0xfffff
        return 8_800_000 + base
    }
}
