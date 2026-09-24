package org.vpilo.babymonitor.errorreport.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import babymonitor.errorreport.presentation.generated.resources.Res
import babymonitor.errorreport.presentation.generated.resources.error_report_building_title
import babymonitor.errorreport.presentation.generated.resources.error_report_close
import babymonitor.errorreport.presentation.generated.resources.error_report_dont_send
import babymonitor.errorreport.presentation.generated.resources.error_report_failed_message
import babymonitor.errorreport.presentation.generated.resources.error_report_failed_title
import babymonitor.errorreport.presentation.generated.resources.error_report_handoff_attach
import babymonitor.errorreport.presentation.generated.resources.error_report_handoff_attach_no_mail_client
import babymonitor.errorreport.presentation.generated.resources.error_report_handoff_shared
import babymonitor.errorreport.presentation.generated.resources.error_report_handoff_title
import babymonitor.errorreport.presentation.generated.resources.error_report_prompt_message
import babymonitor.errorreport.presentation.generated.resources.error_report_prompt_title
import babymonitor.errorreport.presentation.generated.resources.error_report_send
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.vpilo.babymonitor.errorreport.model.DEVELOPER_EMAIL
import org.vpilo.babymonitor.errorreport.model.SessionCrashReason
import org.vpilo.babymonitor.presentation.AppPreviewTheme
import org.vpilo.babymonitor.presentation.Theme
import org.vpilo.babymonitor.presentation.composables.LoadingIcon

/**
 * Offers to send a report about the previous session, when it ended with a failure.
 */
@Composable
fun ErrorReportDialog(viewModel: ErrorReportViewModel = koinViewModel()) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    if (state is ErrorReportState.None) return

    ErrorReportDialogContent(
        state = state,
        onDismiss = { viewModel.send(ErrorReportScreenAction.Dismiss) },
        onSendReport = { viewModel.send(ErrorReportScreenAction.Send) },
    )
}

@Composable
private fun ErrorReportDialogContent(
    state: ErrorReportState,
    onDismiss: () -> Unit = {},
    onSendReport: () -> Unit = {},
) {
    // The report is lost when dismissed, so only an explicit button click may do that.
    val properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)

    when (state) {
        ErrorReportState.None -> {
            // Nothing to show.
        }

        is ErrorReportState.Prompt -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                properties = properties,
                title = { Text(stringResource(Res.string.error_report_prompt_title)) },
                text = { Text(stringResource(Res.string.error_report_prompt_message)) },
                confirmButton = {
                    TextButton(onClick = onSendReport) {
                        Text(stringResource(Res.string.error_report_send))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(Res.string.error_report_dont_send))
                    }
                },
            )
        }

        ErrorReportState.Building -> {
            AlertDialog(
                onDismissRequest = {},
                properties = properties,
                title = { Text(stringResource(Res.string.error_report_building_title)) },
                text = {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        LoadingIcon(modifier = Modifier.size(Theme.Sizes.IconLarge))
                    }
                },
                confirmButton = {},
            )
        }

        is ErrorReportState.Handoff -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                properties = properties,
                title = { Text(stringResource(Res.string.error_report_handoff_title)) },
                text = { HandoffMessage(state) },
                confirmButton = { CloseButton(onDismiss) },
            )
        }

        ErrorReportState.Failed -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                properties = properties,
                title = { Text(stringResource(Res.string.error_report_failed_title)) },
                text = { Text(stringResource(Res.string.error_report_failed_message)) },
                confirmButton = { CloseButton(onDismiss) },
            )
        }
    }
}

@Composable
private fun HandoffMessage(state: ErrorReportState.Handoff) {
    val displayPath = state.displayPath
    if (displayPath == null) {
        Text(stringResource(Res.string.error_report_handoff_shared))
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(Theme.Paddings.Medium)) {
        Text(
            if (state.isHandoffSucceeded) {
                stringResource(Res.string.error_report_handoff_attach)
            } else {
                stringResource(Res.string.error_report_handoff_attach_no_mail_client, DEVELOPER_EMAIL)
            },
        )
        SelectionContainer {
            Text(displayPath, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CloseButton(onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(stringResource(Res.string.error_report_close))
    }
}

@Preview(widthDp = 400, heightDp = 300)
@Composable
private fun ErrorReportPromptPreview() =
    AppPreviewTheme {
        ErrorReportDialogContent(state = ErrorReportState.Prompt(SessionCrashReason.AbnormalSession))
    }

@Preview(widthDp = 400, heightDp = 300)
@Composable
private fun ErrorReportBuildingPreview() =
    AppPreviewTheme(modifier = Modifier.fillMaxSize()) {
        Box { ErrorReportDialogContent(state = ErrorReportState.Building) }
    }

@Preview(widthDp = 400, heightDp = 300)
@Composable
private fun ErrorReportHandoffDesktopPreview() =
    AppPreviewTheme {
        ErrorReportDialogContent(
            state =
                ErrorReportState.Handoff(
                    displayPath = "/home/user/.config/babymonitor/nanna-baby-monitor-report-20260921-120000.zip",
                    isHandoffSucceeded = false,
                ),
        )
    }

@Preview(widthDp = 400, heightDp = 300)
@Composable
private fun ErrorReportFailedPreview() =
    AppPreviewTheme {
        ErrorReportDialogContent(state = ErrorReportState.Failed)
    }
