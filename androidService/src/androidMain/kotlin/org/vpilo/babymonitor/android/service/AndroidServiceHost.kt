package org.vpilo.babymonitor.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
import androidx.core.app.NotificationCompat
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.AppRole

/**
 * Empty service to keep the app running in the background while the camera is active.
 */
internal class AndroidServiceHost : LifecycleService() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Logger.d(TAG) { "Created service" }
        AndroidServiceRegistry.reportServiceStarted(this)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        Logger.d(TAG) { "Starting service in foreground" }
        super.onStartCommand(intent, flags, startId)
        startForeground(NOTIFICATION_ID, createNotification(), foregroundServiceType())
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Logger.d(TAG) { "Destroyed service" }
        AndroidServiceRegistry.reportServiceStopped()
    }

    private fun foregroundServiceType(): Int =
        when (AndroidServiceRegistry.currentRole) {
            AppRole.SERVER -> FOREGROUND_SERVICE_TYPE_CAMERA or FOREGROUND_SERVICE_TYPE_MICROPHONE
            AppRole.CLIENT -> FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            AppRole.UNDECIDED -> error("Foreground service started with no registered role")
        }

    private fun createNotificationChannel() {
        val serviceChannel =
            NotificationChannel(
                CHANNEL_ID,
                "Camera active",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(serviceChannel)
    }

    private fun createNotification(): Notification =
        NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setContentTitle("Baby Monitor")
            .setContentText("Camera is recording.")
            .setSmallIcon(R.drawable.ic_launcher)
            .build()

    companion object {
        private val TAG = AndroidServiceHost::class

        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "BackgroundServiceChannel"
    }
}
