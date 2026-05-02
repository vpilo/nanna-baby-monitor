package org.vpilo.babymonitor.network.server

/**
 * Platform hook tying the lifetime of any required platform-level "foreground" affordance to the server's lifetime.
 */
internal expect class ServerForegroundServiceLink() {
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
