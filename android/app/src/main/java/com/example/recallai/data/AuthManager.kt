package com.example.recallai.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * In-memory session + profile fields backed by Compose snapshot state so UI updates immediately.
 * Persisted profile fields are merged via [RecallAiPreferences].
 */
object AuthManager {
    var token: String? by mutableStateOf(null)
    /** Mongo user id from login / register. */
    var userId: String? by mutableStateOf(null)
    var userName: String? by mutableStateOf(null)
    var userGender: String? by mutableStateOf(null)
    var userRole: String? by mutableStateOf(null)

    /** Absolute path to a JPEG copied into app storage; null uses placeholder avatar. */
    var avatarLocalPath: String? by mutableStateOf(null)

    /** Short bio shown in profile (device-local). */
    var bio: String? by mutableStateOf(null)
}
