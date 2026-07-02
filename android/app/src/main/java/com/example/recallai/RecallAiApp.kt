package com.example.recallai

import android.app.Application
import android.os.StrictMode
import com.example.recallai.BuildConfig
import com.example.recallai.data.AuthManager
import com.example.recallai.data.BackendConfig
import com.example.recallai.data.CareRepository
import com.example.recallai.data.RecallAiPreferences
import com.example.recallai.fcm.FcmRegistrar
import com.example.recallai.notifications.RecallNotifications
import com.example.recallai.sync.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@HiltAndroidApp
class RecallAiApp : Application() {

    @Inject lateinit var careRepository: CareRepository

    override fun onCreate() {
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .build()
            )
        }
        super.onCreate()
        BackendConfig.init(this)
        RecallNotifications.ensureAllChannels(this)
        // Defer prefs read so Application.onCreate returns quickly (first frame / cold start).
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            runCatching { RecallAiPreferences.syncHapticsFromDisk(this@RecallAiApp) }
            if (!AuthManager.token.isNullOrBlank()) {
                CoroutineScope(Dispatchers.IO).launch {
                    runCatching { FcmRegistrar.registerIfLoggedIn(careRepository) }
                }
                runCatching { SyncScheduler.schedulePeriodic(this@RecallAiApp) }
            }
        }
    }
}