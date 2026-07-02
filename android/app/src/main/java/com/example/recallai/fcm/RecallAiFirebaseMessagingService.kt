package com.example.recallai.fcm

import com.example.recallai.data.AuthManager
import com.example.recallai.data.CareRepository
import com.example.recallai.notifications.RecallNotifications
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RecallAiFirebaseMessagingService : FirebaseMessagingService() {

    @Inject lateinit var careRepository: CareRepository

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        CoroutineScope(Dispatchers.IO).launch {
            if (AuthManager.token.isNullOrBlank()) return@launch
            runCatching { careRepository.registerDeviceToken(token) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val dataType = message.data["type"]?.lowercase().orEmpty()
        val title = message.notification?.title
            ?: message.data["title"]
            ?: getString(com.example.recallai.R.string.app_name)
        val body = message.notification?.body ?: message.data["body"] ?: ""
        if (title.isBlank() && body.isBlank()) return

        RecallNotifications.showFromFcmData(
            context = this,
            dataType = dataType,
            title = title,
            body = body,
            screen = message.data["screen"]
        )
    }
}
