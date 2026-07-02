package com.example.recallai.data

import android.content.Context
import android.content.SharedPreferences
import java.io.File

/**
 * App-wide settings in SharedPreferences. Haptics share the same store/keys as
 * [CaregiverRulesRepository] so Alert Rules and the Settings menu stay aligned.
 *
 * Profile fields are stored **per role** (patient vs caregiver) so a device-local patient
 * display name never overrides the caregiver account after login.
 */
object RecallAiPreferences {
    private const val PREFS_SETTINGS = "recallai_settings"
    private const val KEY_NOTIFY_ENABLED = "notify_enabled"

    /** Legacy keys (pre–role-scoped profile); migrated once into role-specific keys. */
    private const val KEY_PROFILE_DISPLAY_NAME = "profile_display_name"
    private const val KEY_PROFILE_AVATAR_PATH = "profile_avatar_abs_path"
    private const val KEY_PROFILE_BIO = "profile_bio"
    private const val KEY_PROFILE_GENDER = "profile_gender"
    private const val KEY_LOCAL_PROFILE_SAVED = "profile_local_saved"

    // Must match CaregiverRulesRepository companion values.
    private const val PREFS_CAREGIVER_RULES = "recallai_caregiver_rules"
    private const val KEY_REDUCE_HAPTICS = "reduce_haptics"

    private fun settingsPrefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)

    private fun rulesPrefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_CAREGIVER_RULES, Context.MODE_PRIVATE)

    private fun normalizedRole(): String =
        AuthManager.userRole?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: "patient"

    private fun keyLocalSaved(role: String) = "profile_local_saved_${role.lowercase()}"
    private fun keyDisplayName(role: String) = "profile_display_name_${role.lowercase()}"
    private fun keyAvatar(role: String) = "profile_avatar_abs_path_${role.lowercase()}"
    private fun keyBio(role: String) = "profile_bio_${role.lowercase()}"
    private fun keyGender(role: String) = "profile_gender_${role.lowercase()}"

    /**
     * One-time: move legacy single-profile keys into the **patient** namespace so caregiver
     * logins are no longer overwritten by an old patient-local profile.
     */
    private fun migrateLegacyProfileIfNeeded(sp: SharedPreferences) {
        if (!sp.getBoolean(KEY_LOCAL_PROFILE_SAVED, false)) return
        if (sp.getBoolean(keyLocalSaved("patient"), false)) {
            sp.edit().remove(KEY_LOCAL_PROFILE_SAVED).apply()
            return
        }
        val ed = sp.edit()
        ed.putBoolean(keyLocalSaved("patient"), true)
        sp.getString(KEY_PROFILE_DISPLAY_NAME, null)?.takeIf { it.isNotBlank() }?.let {
            ed.putString(keyDisplayName("patient"), it)
        }
        sp.getString(KEY_PROFILE_GENDER, null)?.takeIf { it.isNotBlank() }?.let {
            ed.putString(keyGender("patient"), it)
        }
        sp.getString(KEY_PROFILE_BIO, null)?.takeIf { it.isNotBlank() }?.let {
            ed.putString(keyBio("patient"), it)
        }
        sp.getString(KEY_PROFILE_AVATAR_PATH, null)?.takeIf { it.isNotBlank() }?.let {
            ed.putString(keyAvatar("patient"), it)
        }
        ed.remove(KEY_LOCAL_PROFILE_SAVED)
        ed.remove(KEY_PROFILE_DISPLAY_NAME)
        ed.remove(KEY_PROFILE_GENDER)
        ed.remove(KEY_PROFILE_BIO)
        ed.remove(KEY_PROFILE_AVATAR_PATH)
        ed.apply()
    }

    fun isNotifyEnabled(context: Context): Boolean =
        settingsPrefs(context).getBoolean(KEY_NOTIFY_ENABLED, true)

    fun setNotifyEnabled(context: Context, enabled: Boolean) {
        settingsPrefs(context).edit().putBoolean(KEY_NOTIFY_ENABLED, enabled).apply()
    }

    fun isReduceHaptics(context: Context): Boolean =
        rulesPrefs(context).getBoolean(KEY_REDUCE_HAPTICS, false)

    fun setReduceHaptics(context: Context, reduce: Boolean) {
        rulesPrefs(context).edit().putBoolean(KEY_REDUCE_HAPTICS, reduce).apply()
        HapticsConfig.reduceHaptics = reduce
    }

    fun syncHapticsFromDisk(context: Context) {
        HapticsConfig.reduceHaptics = isReduceHaptics(context)
    }

    /**
     * After server auth fills [AuthManager], either restore a saved local profile or persist
     * the server snapshot as the default on-device profile for the **current role**.
     */
    fun mergeProfileAfterServerAuth(context: Context) {
        val sp = settingsPrefs(context)
        migrateLegacyProfileIfNeeded(sp)
        val role = normalizedRole()
        if (sp.getBoolean(keyLocalSaved(role), false)) {
            sp.getString(keyDisplayName(role), null)?.takeIf { it.isNotBlank() }?.let {
                AuthManager.userName = it
            }
            sp.getString(keyGender(role), null)?.takeIf { it.isNotBlank() }?.let {
                AuthManager.userGender = it
            }
            AuthManager.bio = sp.getString(keyBio(role), null)?.takeIf { it.isNotBlank() }
            val path = sp.getString(keyAvatar(role), null)
            AuthManager.avatarLocalPath = path?.takeIf { File(it).exists() }
        } else {
            persistProfileSnapshot(context)
        }
    }

    fun persistProfileSnapshot(context: Context) {
        val role = normalizedRole()
        settingsPrefs(context).edit().apply {
            putString(keyDisplayName(role), AuthManager.userName)
            putString(keyGender(role), AuthManager.userGender)
            putString(keyBio(role), AuthManager.bio)
            putString(keyAvatar(role), AuthManager.avatarLocalPath)
            apply()
        }
    }

    /**
     * User saved Profile from Settings — device-local fields take precedence until cleared.
     */
    fun saveLocalProfile(
        context: Context,
        displayName: String,
        bio: String?,
        gender: String?,
        avatarAbsolutePath: String?
    ) {
        AuthManager.userName = displayName.trim().ifBlank { AuthManager.userName }
        AuthManager.bio = bio?.trim()?.takeIf { it.isNotBlank() }
        AuthManager.userGender = gender?.trim()?.takeIf { it.isNotBlank() }
        AuthManager.avatarLocalPath = avatarAbsolutePath?.takeIf { File(it).exists() }
        val role = normalizedRole()
        settingsPrefs(context).edit().apply {
            putBoolean(keyLocalSaved(role), true)
            putString(keyDisplayName(role), AuthManager.userName)
            putString(keyBio(role), AuthManager.bio)
            putString(keyGender(role), AuthManager.userGender)
            putString(keyAvatar(role), AuthManager.avatarLocalPath)
            apply()
        }
    }

    fun copyUriToProfileAvatarFile(context: Context, uri: android.net.Uri): String? {
        return try {
            val role = normalizedRole().replace(Regex("[^a-z0-9]"), "_")
            val dest = File(context.applicationContext.filesDir, "profile_avatar_$role.jpg")
            context.applicationContext.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            dest.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    fun deleteStoredAvatarFile(context: Context) {
        val sp = settingsPrefs(context)
        val role = normalizedRole()
        val path = sp.getString(keyAvatar(role), null) ?: AuthManager.avatarLocalPath
        path?.let { runCatching { File(it).delete() } }
        try {
            val safeRole = normalizedRole().replace(Regex("[^a-z0-9]"), "_")
            File(context.applicationContext.filesDir, "profile_avatar_$safeRole.jpg").delete()
            File(context.applicationContext.filesDir, "profile_avatar.jpg").delete()
        } catch (_: Exception) {
        }
    }
}
