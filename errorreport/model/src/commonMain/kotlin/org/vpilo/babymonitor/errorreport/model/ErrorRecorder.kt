package org.vpilo.babymonitor.errorreport.model

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.common.LoggingForwarder
import org.vpilo.babymonitor.common.PlatformLogger
import java.io.File
import kotlin.concurrent.Volatile
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Records the current session's log to a file, so that an unclean exit can be reported at the next start.
 * A log file left on disk probably means something went wrong, as we delete it on a clean shutdown.
 */
object ErrorRecorder {
    @Volatile
    private var logFile: File? = null

    private val startInstant: Instant = Clock.System.now()

    private val logCache: Channel<String> = Channel(LOG_CAPACITY, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val forwardingLogger: PlatformLogger =
        PlatformLogger { tag, level, message, throwable ->
            logFile ?: return@PlatformLogger

            val elapsedTime =
                (Clock.System.now() - startInstant).toComponents { hours, minutes, seconds, nanoseconds ->
                    buildString {
                        append(hours.toString().padStart(2, '0'))
                        append(":")
                        append(minutes.toString().padStart(2, '0'))
                        append(":")
                        append(seconds.toString().padStart(2, '0'))
                        append(".")
                        append((nanoseconds / 1_000_000).toString().padStart(3, '0')) // Milliseconds are enough
                    }
                }

            val logMessage = "[$elapsedTime][$level][$tag] $message" + throwable?.let { "\n${it.stackTraceToString()}" }.orEmpty()
            logCache.trySend(logMessage)
        }

    private var flushJob: Job? = null

    private var isUncaughtExceptionHandlerInstalled: Boolean = false

    private var isShutdownHookRegistered: Boolean = false

    private var hasRecordedFatalError: Boolean = false

    /**
     * Start recording this session's log to the given [sessionLogFile].
     */
    @Synchronized
    public fun start(sessionLogFile: File) {
        if (logFile != null) return
        logFile = sessionLogFile
        hasRecordedFatalError = false
        LoggingForwarder.enableForwarding(forwardingLogger)

        installUncaughtExceptionHandler()
        registerShutdownHook()

        flushJob?.cancel()
        flushJob =
            CoroutineScope(Dispatchers.IO).launch {
                while (true) {
                    delay(LOG_FLUSH_INTERVAL)
                    flush()
                }
            }
    }

    /**
     * Stop recording this session's log on a clean shutdown.
     * The log is no longer needed so it is deleted, unless it holds a recorded fatal throwable.
     */
    @Synchronized
    public fun stop() {
        val file = logFile ?: return
        LoggingForwarder.disableForwarding()
        Logger.i(this::class) { "STOP" }
        flushJob?.cancel()
        flushJob = null
        logFile = null
        if (!hasRecordedFatalError) {
            file.delete()
            do {
                val discarded = logCache.tryReceive()
            } while (discarded.isSuccess)
        }
    }

    /**
     * Make a [CoroutineExceptionHandler] to ensure uncaught exceptions in coroutines are recorded.
     */
    public fun getCoroutineExceptionHandler(): CoroutineExceptionHandler =
        CoroutineExceptionHandler { context, throwable ->
            logFatal(throwable) { "Uncaught exception in coroutine $context, ${throwable::class.simpleName}" }
            throw throwable
        }

    private fun installUncaughtExceptionHandler() {
        if (isUncaughtExceptionHandlerInstalled) return
        isUncaughtExceptionHandlerInstalled = true

        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                logFatal(throwable) { "Uncaught exception on thread ${thread.name}, ${throwable::class.simpleName}" }
            }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    /**
     * Useful only on desktop.
     */
    private fun registerShutdownHook() {
        if (isShutdownHookRegistered) return
        isShutdownHookRegistered = true
        Runtime.getRuntime().addShutdownHook(Thread { stop() })
    }

    @Synchronized
    private fun logFatal(
        throwable: Throwable,
        message: () -> String,
    ) {
        if (hasRecordedFatalError) return
        Logger.e(this::class, throwable, message)
        flush()
        hasRecordedFatalError = true
    }

    @Synchronized
    private fun flush() {
        if (hasRecordedFatalError) return
        val file = logFile ?: return
        val content =
            buildString {
                do {
                    append(logCache.tryReceive().getOrNull() ?: break)
                    append("\n")
                } while (true)
            }.ifEmpty { return }
        runCatching { file.appendText(content) }
    }

    private const val LOG_CAPACITY = 1_000
    private val LOG_FLUSH_INTERVAL = 2.seconds
}
