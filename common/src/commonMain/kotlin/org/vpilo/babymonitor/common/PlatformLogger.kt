package org.vpilo.babymonitor.common

/**
 * Logging message sink.
 *
 * This allows the application to log important messages to a specific source.
 */
public fun interface PlatformLogger {
    /**
     * Log a [message] belonging to the component indicated by [tag], with a specific priority [level].
     * Optionally, add a [Throwable] in case an exception stack trace should be recorded.
     */
    public fun log(
        tag: String,
        level: LogLevel,
        message: String,
        throwable: Throwable?,
    )
}
