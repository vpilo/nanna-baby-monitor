package org.vpilo.babymonitor.network.internal

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import org.vpilo.babymonitor.android.service.AndroidService
import org.vpilo.babymonitor.android.service.AndroidServiceRegistry
import org.vpilo.babymonitor.model.AppRole

/**
 * Android [BackgroundServiceLink] to keep components alive in the background.
 *
 * This keeps the app's foreground service alive for the duration of the network components' availability, so connections and streaming
 * can keep running while the device is locked.
 * When the service is closed, this will ensure the network components are also closed.
 */
actual class BackgroundServiceLink actual constructor(
    role: AppRole,
    private val onStoppedByPlatform: () -> Unit,
) {
    private val service =
        object : AndroidService {
            override val role: AppRole = role

            override fun onServiceStarted(
                context: Context,
                lifecycleOwner: LifecycleOwner,
            ) = Unit

            override fun onServiceStopped() = onStoppedByPlatform()
        }

    actual fun start() {
        AndroidServiceRegistry.register(service)
    }

    actual fun stop() {
        AndroidServiceRegistry.unregister(service)
    }
}
