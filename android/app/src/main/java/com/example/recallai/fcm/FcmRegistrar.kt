package com.example.recallai.fcm

import com.example.recallai.data.AuthManager
import com.example.recallai.data.CareRepository
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

object FcmRegistrar {
    /** Uploads the current FCM token when the user is already logged in (JWT set). */
    suspend fun registerIfLoggedIn(careRepository: CareRepository) {
        if (AuthManager.token.isNullOrBlank()) return
        val token = runCatching { FirebaseMessaging.getInstance().token.await() }.getOrNull()
            ?: return
        runCatching { careRepository.registerDeviceToken(token) }
    }
}
