package org.vpilo.babymonitor.common

import kotlin.concurrent.Volatile

object LoggingForwarder {
    @Volatile
    private var forwardingLogger: PlatformLogger? = null

    /**
     * Starts additionally forwarding log messages to the given [target].
     */
    public fun enableForwarding(target: PlatformLogger) {
        check(forwardingLogger == null) { "Log forwarding was already enabled" }
        forwardingLogger = target
        Logger.setLogger { tag, level, message, throwable ->
            platformLogger.log(tag, level, message, throwable)
            forwardingLogger?.log(tag, level, message, throwable)
        }
    }

    /**
     * Returns whether log messages are being forwarded to a file.
     */
    public fun isForwardingEnabled(): Boolean = forwardingLogger != null

    /**
     * Stops forwarding log messages to a file.
     */
    public fun disableForwarding() {
        checkNotNull(forwardingLogger) { "Log forwarding was not enabled" }
        forwardingLogger = null
        Logger.setLogger(platformLogger)
    }
}
