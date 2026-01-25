package org.vpilo.babymonitor.camera.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import org.vpilo.babymonitor.common.Logger

class AndroidBackgroundService : LifecycleService() {

    private var camera: AndroidCamera? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Logger.i(this::class) { "Created service" }
        start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.i(this::class) { "Starting service in foreground" }
        super.onStartCommand(intent, flags, startId)
        startForeground(NOTIFICATION_ID, createNotification())
        return START_STICKY
    }

    override fun onDestroy() {
        stop()
        super.onDestroy()
        Logger.i(this::class) { "Destroyed service" }
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
        Logger.i(this::class) { "Starting to record" }
        camera = AndroidCamera(
            context = this,
            lifecycleOwner = this,
            videoFrames = CameraInterface.frameCollector,
            audioSamples = CameraInterface.sampleCollector,
        ).apply { start() }
    }

    fun stop() {
        check(camera != null) { "Camera was not started" }
        Logger.i(this::class) { "Stopping recording" }
        camera?.stop()
        camera = null
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "CameraServiceChannel"

        fun start(context: Context) {
            val intent = Intent(context, AndroidBackgroundService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, AndroidBackgroundService::class.java)
            context.stopService(intent)
        }
    }
}
