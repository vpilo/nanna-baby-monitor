package org.vpilo.babymonitor.common

import kotlin.concurrent.Volatile

internal expect val platformLogger: PlatformLogger

/**
 * Logging abstraction.
 *
 * This singleton allows to log from anywhere, on all supported platforms.
 */
public object Logger {
    @Volatile
    var currentLogger: PlatformLogger = platformLogger
        private set

    internal fun setLogger(logger: PlatformLogger) {
        currentLogger = logger
    }

    /**
     * Log a message at debug level using the given [tag].
     * The [message] is a lazily evaluated lambda: it will be invoked only when the log is called.
     * Optionally, a [throwable] can be provided to log an exception along with the [message].
     */
    public inline fun d(
        tag: String,
        throwable: Throwable? = null,
        message: () -> String,
    ) {
        currentLogger.log(tag, LogLevel.DEBUG, message(), throwable)
    }

    /**
     * Log a message at info level using the given [tag].
     * The [message] is a lazily evaluated lambda: it will be invoked only when the log is called.
     * Optionally, a [throwable] can be provided to log an exception along with the [message].
     */
    public inline fun i(
        tag: String,
        throwable: Throwable? = null,
        message: () -> String,
    ) {
        currentLogger.log(tag, LogLevel.INFO, message(), throwable)
    }

    /**
     * Log a message at warning level using the given [tag].
     * The [message] is a lazily evaluated lambda: it will be invoked only when the log is called.
     * Optionally, a [throwable] can be provided to log an exception along with the [message].
     */
    public inline fun w(
        tag: String,
        throwable: Throwable? = null,
        message: () -> String,
    ) {
        currentLogger.log(tag, LogLevel.WARN, message(), throwable)
    }

    /**
     * Log a message at error level using the given [tag].
     * The [message] is a lazily evaluated lambda: it will be invoked only when the log is called.
     * Optionally, a [throwable] can be provided to log an exception along with the [message].
     */
    public inline fun e(
        tag: String,
        throwable: Throwable? = null,
        message: () -> String,
    ) {
        currentLogger.log(tag, LogLevel.ERROR, message(), throwable)
    }
}
