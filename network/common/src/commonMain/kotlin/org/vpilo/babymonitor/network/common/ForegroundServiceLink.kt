package org.vpilo.babymonitor.network.common

import org.vpilo.babymonitor.model.AppRole

/**
 * Platform hook tying the lifetime of any required platform-level "foreground" affordance to the server's lifetime.
 * On construction, it needs the current app [role], used to determine which platform permissions to request.
 */
expect class ForegroundServiceLink(
    role: AppRole,
) {
    /**
     * Starts the foreground service to keep the server alive in the background.
     *
     * Must be called from a context where the platform allows starting the foreground affordance.
     */
    fun start()

    /**
     * Stops the foreground service.
     *
     * Must be called when the server stops.
     */
    fun stop()
}
