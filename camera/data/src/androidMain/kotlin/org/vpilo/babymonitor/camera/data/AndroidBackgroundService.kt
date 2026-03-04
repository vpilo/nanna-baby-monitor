package org.vpilo.babymonitor.camera.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import org.vpilo.babymonitor.common.Logger
import kotlin.concurrent.Volatile

internal class AndroidBackgroundService : LifecycleService() {

    private var camera: AndroidCamera? = null

    override fun onCreate() {
        check(instance == null) { "Attempted to create service twice" }
        instance = this
        super.onCreate()
        createNotificationChannel()
        Logger.d(TAG) { "Created service" }
        start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.d(TAG) { "Starting service in foreground" }
        super.onStartCommand(intent, flags, startId)
        startForeground(NOTIFICATION_ID, createNotification())
        return START_STICKY
    }

    override fun onDestroy() {
        checkNotNull(instance) { "Attempted to destroy service twice" }
        instance = null
        stop()
        super.onDestroy()
        Logger.d(TAG) { "Destroyed service" }
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "Camera active",
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Baby Monitor Active")
            .setContentText("Camera is recording.")
            .setSmallIcon(R.drawable.ic_launcher)
            .build()
    }

    fun start() {
        check(camera == null) { "Camera already started" }
        Logger.d(TAG) { "Starting to record" }
        camera = AndroidCamera(
            context = this,
            lifecycleOwner = this,
            videoFrames = CameraInterface.frameCollector,
            audioSamples = CameraInterface.sampleCollector,
        ).apply { start() }
    }

    fun stop() {
        check(camera != null) { "Camera was not started" }
        Logger.d(TAG) { "Stopping recording" }
        camera?.stop()
        camera = null
    }

    companion object {
        private val TAG = AndroidBackgroundService::class

        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "CameraServiceChannel"

        @Volatile
        private var instance: AndroidBackgroundService? = null

        fun start(context: Context) {
            if (instance != null) {
                Logger.w(TAG) { "Attempted to create service twice" }
                return
            }
            val intent = Intent(context, AndroidBackgroundService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            if (instance == null) {
                Logger.w(TAG) { "Attempted to destroy service twice" }
                return
            }
            val intent = Intent(context, AndroidBackgroundService::class.java)
            context.stopService(intent)
        }

        fun isStarted(): Boolean =
            instance != null
    }
}
