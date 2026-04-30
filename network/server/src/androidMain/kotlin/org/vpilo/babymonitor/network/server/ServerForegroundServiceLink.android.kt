package org.vpilo.babymonitor.network.server

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import org.vpilo.babymonitor.android.service.AndroidService
import org.vpilo.babymonitor.android.service.AndroidServiceRegistry

/**
 * Android [ServerForegroundServiceLink] to keep the server alive in the background.
 *
 * This keeps the app's foreground service alive for the duration of the server's availability, so the camera/audio capture can keep
 * running while the device is locked.
 */
internal actual class ServerForegroundServiceLink actual constructor() : AndroidService {
    actual fun start() {
        AndroidServiceRegistry.register(this)
    }

    actual fun stop() {
        AndroidServiceRegistry.unregister(this)
    }

    override fun onServiceStarted(
        context: Context,
        lifecycleOwner: LifecycleOwner,
    ) = Unit

    override fun onServiceStopped() = Unit
}
