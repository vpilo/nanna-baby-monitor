package org.vpilo.babymonitor.errorreport.model

import kotlin.time.Instant

/**
 * Why a previous session is considered to have ended in a crash.
 */
sealed interface SessionCrashReason {
    /**
     * The session's log ends with an error: the app died on an uncaught throwable.
     */
    data object UncaughtException : SessionCrashReason

    /**
     * The OS recorded the process death with a reason worth reporting.
     */
    data class KilledByOs(
        val reasonCode: Int,
        val reasonName: String,
        val description: String?,
        val importance: Int,
        val timestamp: Instant,
        val sessionActive: Boolean,
    ) : SessionCrashReason

    /**
     * The app died while streaming.
     * No information why is available.
     */
    data object AbnormalSession : SessionCrashReason
}
