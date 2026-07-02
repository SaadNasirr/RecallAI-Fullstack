package com.example.recallai.data

import android.content.Context
import androidx.core.content.edit

class TokenStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) = prefs.edit { putString(KEY_TOKEN, value) }

    var userId: String?
        get() = prefs.getString(KEY_USER_ID, null)
        set(value) = prefs.edit { putString(KEY_USER_ID, value) }

    var role: String?
        get() = prefs.getString(KEY_ROLE, null)
        set(value) = prefs.edit { putString(KEY_ROLE, value) }

    fun clear() = prefs.edit {
        remove(KEY_TOKEN)
        remove(KEY_USER_ID)
        remove(KEY_ROLE)
    }

    companion object {
        private const val PREFS_NAME = "recallai_auth"
        private const val KEY_TOKEN = "token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_ROLE = "role"
    }
}