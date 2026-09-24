package org.vpilo.babymonitor.errorreport.presentation

import androidx.compose.runtime.Stable
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.errorreport.model.ErrorReportingRepository
import org.vpilo.babymonitor.model.viewmodel.AppViewModel

@Stable
class ErrorReportViewModel(
    private val errorReportingRepository: ErrorReportingRepository,
) : AppViewModel<ErrorReportScreenAction, ErrorReportState, Unit>(
        initialState = ErrorReportState.None,
    ) {
    override fun SubscriptionScope.onSubscribed() {
        errorReportingRepository.pendingReport.subscribe { report ->
            when {
                report == null -> ErrorReportState.None.update()
                state == ErrorReportState.None -> ErrorReportState.Prompt(report.reason).update()
            }
        }
    }

    override fun onAction(action: ErrorReportScreenAction) {
        when (action) {
            ErrorReportScreenAction.Send -> {
                send()
            }

            ErrorReportScreenAction.Dismiss -> {
                vmScope.launch { errorReportingRepository.discard() }
            }
        }
    }

    private fun send() {
        if (state !is ErrorReportState.Prompt) return
        ErrorReportState.Building.update()
        vmScope.launch {
            errorReportingRepository
                .send()
                .onSuccess {
                    ErrorReportState.Handoff(displayPath = it.displayPath, isHandoffSucceeded = it.isSucceeded).update()
                }.onFailure {
                    Logger.w(TAG, it) { "Unable to send the error report" }
                    ErrorReportState.Failed.update()
                }
        }
    }
}
