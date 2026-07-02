package com.example.recallai.data

import android.content.Context
import androidx.core.content.edit

object BackendConfig {
    private const val PREFS_NAME = "recallai_backend"
    private const val KEY_BASE_URL = "base_url"

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun getOverrideBaseUrl(): String? {
        if (!::appContext.isInitialized) return null
        return appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_BASE_URL, null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    fun setOverrideBaseUrl(url: String) {
        if (!::appContext.isInitialized) return
        val normalized = normalize(url)
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putString(KEY_BASE_URL, normalized) }
    }

    fun clearOverrideBaseUrl() {
        if (!::appContext.isInitialized) return
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { remove(KEY_BASE_URL) }
    }

    private fun normalize(url: String): String {
        val withScheme = if (url.startsWith("http://") || url.startsWith("https://")) {
            url
        } else {
            "https://$url"
        }
        return withScheme.trim().trimEnd('/') + "/"
    }
}
