package com.example.recallai.reminders

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.recallai.R

/**
 * Foreground service: looping alarm tone + vibration until [stop] (Done / Dismiss).
 * The foreground notification is the main alarm UI — ongoing until dismissed.
 */
class PatientAlarmSoundService : Service() {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val alarmId = intent?.getLongExtra(EXTRA_ALARM_ID, -1L) ?: -1L
        val label = intent?.getStringExtra(EXTRA_LABEL)?.trim().orEmpty().ifBlank { "Alarm" }
        if (alarmId <= 0L) {
            stopSelf()
            return START_NOT_STICKY
        }

        ReminderNotificationController.ensureAlarmChannel(this)

        val notifId = PatientAlarmNotifications.notificationId(alarmId)
        val notif = NotificationCompat.Builder(this, ReminderNotificationController.ALARM_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Alarm")
            .setContentText(label)
            .setStyle(NotificationCompat.BigTextStyle().bigText(label))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(false)
            .addAction(0, "Done", PatientAlarmActionReceiver.ackIntent(this, alarmId))
            .addAction(0, "Dismiss", PatientAlarmActionReceiver.dismissIntent(this, alarmId))
            .apply {
                if (Build.VERSION.SDK_INT >= 34) {
                    setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                }
            }
            .build()

        startForeground(notifId, notif)

        val uri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        stopPlaybackInternal()
        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            setDataSource(applicationContext, uri)
            isLooping = true
            prepare()
            start()
        }

        vibrator = ContextCompat.getSystemService(this, Vibrator::class.java)
        val pattern = longArrayOf(0, 600, 300, 600)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(Service.STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopPlaybackInternal()
        super.onDestroy()
    }

    private fun stopPlaybackInternal() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        vibrator?.cancel()
        vibrator = null
    }

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_LABEL = "alarm_label"

        fun stop(context: Context) {
            context.stopService(Intent(context, PatientAlarmSoundService::class.java))
        }
    }
}
