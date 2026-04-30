package org.vpilo.babymonitor.network.server

/**
 * Android [ServerForegroundServiceLink] to keep the server alive in the background.
 * This does nothing, as on Desktop there are no restrictions to keep the server running in the background.
 */
internal actual class ServerForegroundServiceLink actual constructor() {
    actual fun start() = Unit

    actual fun stop() = Unit
}
