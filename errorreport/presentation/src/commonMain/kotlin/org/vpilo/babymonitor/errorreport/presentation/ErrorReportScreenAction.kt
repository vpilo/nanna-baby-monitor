package org.vpilo.babymonitor.errorreport.presentation

sealed interface ErrorReportScreenAction {
    data object Send : ErrorReportScreenAction

    data object Dismiss : ErrorReportScreenAction
}
