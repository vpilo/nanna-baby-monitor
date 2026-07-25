package org.vpilo.babymonitor.network.internal

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import org.vpilo.babymonitor.android.service.AndroidService
import org.vpilo.babymonitor.android.service.AndroidServiceRegistry
import org.vpilo.babymonitor.model.AppRole

/**
 * Android [ForegroundServiceLink] to keep components alive in the background.
 *
 * This keeps the app's foreground service alive for the duration of the network components' availability, so connections and streaming
 * can keep running while the device is locked.
 */
actual class ForegroundServiceLink actual constructor(
    role: AppRole,
) {
    private val service =
        object : AndroidService {
            override val role: AppRole = role

            override fun onServiceStarted(
                context: Context,
                lifecycleOwner: LifecycleOwner,
            ) = Unit

            override fun onServiceStopped() = Unit
        }

    actual fun start() {
        AndroidServiceRegistry.register(service)
    }

    actual fun stop() {
        AndroidServiceRegistry.unregister(service)
    }
}
