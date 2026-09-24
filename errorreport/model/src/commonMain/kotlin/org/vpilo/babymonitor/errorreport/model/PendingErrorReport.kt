package org.vpilo.babymonitor.errorreport.model

/**
 * A report about the previous session, which the user may choose to send.
 */
data class PendingErrorReport(
    val reason: SessionCrashReason,
)
