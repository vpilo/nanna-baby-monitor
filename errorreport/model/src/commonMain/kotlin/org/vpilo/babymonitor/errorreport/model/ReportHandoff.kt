package org.vpilo.babymonitor.errorreport.model

/**
 * An error report handed over to the user's mail client, or to the share sheet. Whether it is actually sent is unknown.
 */
data class ReportHandoff(
    /**
     * Where the report file is, when the user has to attach it by hand.
     */
    val displayPath: String?,
    /**
     * Whether a new email to the developer was created or the Android share sheet was opened.
     */
    val isSucceeded: Boolean,
)
