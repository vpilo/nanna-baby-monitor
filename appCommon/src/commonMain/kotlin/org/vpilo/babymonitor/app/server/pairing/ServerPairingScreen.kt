package org.vpilo.babymonitor.app.server.pairing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import babymonitor.appcommon.generated.resources.Res
import babymonitor.appcommon.generated.resources.app_title_server_pairing
import babymonitor.appcommon.generated.resources.pairing_enter_pin
import babymonitor.appcommon.generated.resources.pairing_failed_expired
import babymonitor.appcommon.generated.resources.pairing_failed_lockout
import babymonitor.appcommon.generated.resources.pairing_retry
import babymonitor.appcommon.generated.resources.pairing_succeeded
import org.jetbrains.compose.resources.stringResource
import org.vpilo.babymonitor.model.repository.PairingFailureReason
import org.vpilo.babymonitor.model.repository.PairingWindowState
import org.vpilo.babymonitor.presentation.AppPreviewTheme
import org.vpilo.babymonitor.presentation.Theme
import org.vpilo.babymonitor.presentation.composables.AppDestination
import org.vpilo.babymonitor.presentation.composables.LoadingBox
import org.vpilo.babymonitor.presentation.composables.QrCodeImage

@Composable
fun ServerPairingScreen(
    modifier: Modifier = Modifier,
    viewModel: ServerPairingScreenViewModel,
    onBackClicked: () -> Unit,
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    AppDestination(
        modifier = modifier,
        title = Res.string.app_title_server_pairing,
        onMainActionClicked = onBackClicked,
    ) {
        ServerPairingContent(
            modifier = Modifier.fillMaxSize(),
            pairingState = state.pairingState,
            onRetry = { viewModel.send(ServerPairingScreenAction.Retry) },
        )
    }
}

@Composable
private fun ServerPairingContent(
    modifier: Modifier,
    pairingState: PairingWindowState,
    onRetry: () -> Unit,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when (pairingState) {
            PairingWindowState.Idle -> {
                LoadingBox()
            }

            is PairingWindowState.Active -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Theme.Paddings.Medium),
                ) {
                    QrCodeImage(data = pairingState.qrText, modifier = Modifier.size(240.dp))
                    Text(text = stringResource(Res.string.pairing_enter_pin), style = MaterialTheme.typography.bodyMedium)
                    Text(text = pairingState.pin, style = MaterialTheme.typography.displaySmall)
                }
            }

            is PairingWindowState.Succeeded -> {
                Text(
                    text = stringResource(Res.string.pairing_succeeded, pairingState.clientName),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            is PairingWindowState.Failed -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Theme.Paddings.Medium),
                ) {
                    Text(
                        text =
                            stringResource(
                                when (pairingState.reason) {
                                    PairingFailureReason.WRONG_PIN_LOCKOUT -> Res.string.pairing_failed_lockout
                                    PairingFailureReason.WINDOW_EXPIRED -> Res.string.pairing_failed_expired
                                },
                            ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = onRetry) {
                        Text(text = stringResource(Res.string.pairing_retry))
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun ServerPairingContentActivePreview() =
    AppPreviewTheme {
        ServerPairingContent(
            modifier = Modifier.fillMaxSize(),
            pairingState =
                PairingWindowState.Active(
                    pin = "AB23CD",
                    qrText = "bm|1|00000000-0000-0000-0000-000000000000|AB23CD|192.168.1.1",
                ),
            onRetry = {},
        )
    }
