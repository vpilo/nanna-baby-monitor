package org.vpilo.babymonitor.errorreport.presentation

import org.vpilo.babymonitor.errorreport.model.SessionCrashReason

sealed interface ErrorReportState {
    data object None : ErrorReportState

    data class Prompt(
        val reason: SessionCrashReason,
    ) : ErrorReportState

    data object Building : ErrorReportState

    /**
     * The report was handed over to the mail client. Whether it was sent is unknown.
     */
    data class Handoff(
        /**
         * Where the report file is, when the user has to attach it by hand.
         */
        val displayPath: String?,
        val isMailClientOpened: Boolean,
    ) : ErrorReportState

    data object Failed : ErrorReportState
}
