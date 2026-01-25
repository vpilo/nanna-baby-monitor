package org.vpilo.babymonitor.common

import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val logger: Logger by lazy {
    LoggerFactory.getLogger("org.vpilo.babymonitor")
}

actual val platformLogger: PlatformLogger =
    PlatformLogger { tag, level, message, throwable ->
        val logMessage = "[$tag] $message"
        when (level) {
            LogLevel.DEBUG -> logger.debug(logMessage, throwable)
            LogLevel.INFO -> logger.info(logMessage, throwable)
            LogLevel.WARN -> logger.warn(logMessage, throwable)
            LogLevel.ERROR -> logger.error(logMessage, throwable)
        }
    }
