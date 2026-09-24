package org.vpilo.babymonitor.errorreport.model

import kotlinx.coroutines.flow.StateFlow

/**
 * Repository for error reporting, including session recording and pending report management.
 */
interface ErrorReportingRepository {
    /**
     * The report about the previous session, if it ended with a failure.
     * Null until the previous session has been examined, and after the report is discarded.
     */
    val pendingReport: StateFlow<PendingErrorReport?>

    /**
     * Start recording a new session, and examine the previous one.
     * Does nothing while a session is already being recorded.
     */
    fun startNewSession()

    /**
     * Notify the repository when the session is active.
     * The server is active while streaming and when available to connect; the client is available when connected or reconnecting.
     */
    suspend fun markSessionActive(active: Boolean)

    /**
     * Build the pending report, and hand it over to the user's mail client or to the share sheet.
     */
    suspend fun send(): Result<ReportHandoff>

    /**
     * Delete the pending report, the previous session's files, and any report file handed over to the mail client or share sheet.
     */
    suspend fun discard()
}
