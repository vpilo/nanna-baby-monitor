package org.vpilo.babymonitor.errorreport.model

/**
 * An error report handed over to the user's mail client. Whether the email is actually sent is unknown.
 */
data class ReportHandoff(
    /**
     * Where the report file is, when the user has to attach it by hand.
     */
    val displayPath: String?,
    /**
     * Whether a new email to the developer was opened. When not, the user has to write it themselves.
     */
    val isMailClientOpened: Boolean,
)
