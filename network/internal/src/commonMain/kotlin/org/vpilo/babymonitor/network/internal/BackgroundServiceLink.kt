package org.vpilo.babymonitor.network.internal

import org.vpilo.babymonitor.model.AppRole

/**
 * Platform hook linking the lifecycle of the network services to platform-level background service permissions.
 * This allows the network services to continue running while the app is in the background, and ensures they are stopped when the app
 * gets closed.
 * Needs the current app [role] to determine which platform permissions to request. The [onStoppedByPlatform] callback is invoked when the
 * background operation is stopped by the platform.
 */
expect class BackgroundServiceLink(
    role: AppRole,
    onStoppedByPlatform: () -> Unit,
) {
    /**
     * Starts the link to keep the server alive in the background.
     * Must be called from a context where the platform allows starting the foreground affordance.
     */
    fun start()

    /**
     * Stops the background service link.
     */
    fun stop()
}
