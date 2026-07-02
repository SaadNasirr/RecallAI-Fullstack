package com.example.recallai.data

import android.content.Context
import androidx.core.content.edit

data class CaregiverRules(
    val riskThresholdPercent: Int = 60,
    val inactivityDays: Int = 3,
    val reduceHaptics: Boolean = false
)

class CaregiverRulesRepository(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getRules(): CaregiverRules {
        return CaregiverRules(
            riskThresholdPercent = prefs.getInt(KEY_RISK_THRESHOLD, 60),
            inactivityDays = prefs.getInt(KEY_INACTIVITY_DAYS, 3),
            reduceHaptics = prefs.getBoolean(KEY_REDUCE_HAPTICS, false)
        )
    }

    fun saveRules(rules: CaregiverRules) {
        prefs.edit {
            putInt(KEY_RISK_THRESHOLD, rules.riskThresholdPercent)
            putInt(KEY_INACTIVITY_DAYS, rules.inactivityDays)
            putBoolean(KEY_REDUCE_HAPTICS, rules.reduceHaptics)
        }
    }

    companion object {
        private const val PREFS_NAME = "recallai_caregiver_rules"
        private const val KEY_RISK_THRESHOLD = "risk_threshold_percent"
        private const val KEY_INACTIVITY_DAYS = "inactivity_days"
        private const val KEY_REDUCE_HAPTICS = "reduce_haptics"
    }
}

