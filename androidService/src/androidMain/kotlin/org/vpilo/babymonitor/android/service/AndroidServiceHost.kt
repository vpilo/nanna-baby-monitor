package org.vpilo.babymonitor.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
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
        Logger.d(TAG) { "Starting foreground service" }
        super.onStartCommand(intent, flags, startId)
        ServiceCompat.startForeground(this, NOTIFICATION_ID, createNotification(), foregroundServiceType())
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Logger.d(TAG) { "Destroyed service" }
        AndroidServiceRegistry.reportServiceStopped()
    }

    private fun foregroundServiceType(): Int =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            0
        } else {
            when (AndroidServiceRegistry.currentRole) {
                AppRole.SERVER -> FOREGROUND_SERVICE_TYPE_CAMERA or FOREGROUND_SERVICE_TYPE_MICROPHONE
                AppRole.CLIENT -> FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                AppRole.UNDECIDED -> error("Foreground service started with no registered role")
            }
        }

    private fun createNotificationChannel() {
        val serviceChannel =
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.service_notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(serviceChannel)
    }

    private fun createNotification(): Notification {
        val textRes =
            when (AndroidServiceRegistry.currentRole) {
                AppRole.SERVER -> R.string.service_description_server
                AppRole.CLIENT -> R.string.service_description_client
                AppRole.UNDECIDED -> error("Foreground service started with no registered role")
            }
        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_title))
            .setContentText(getString(textRes))
            .setSmallIcon(R.drawable.ic_launcher)
            .build()
    }

    companion object {
        private val TAG = AndroidServiceHost::class

        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "BackgroundServiceChannel"
    }
}
