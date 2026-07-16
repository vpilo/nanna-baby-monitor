package org.vpilo.babymonitor.app.client.pairing

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import babymonitor.appcommon.generated.resources.Res
import babymonitor.appcommon.generated.resources.app_title_client_pairing
import babymonitor.appcommon.generated.resources.client_pairing_failed_connection_failed
import babymonitor.appcommon.generated.resources.client_pairing_failed_mitm_suspected
import babymonitor.appcommon.generated.resources.client_pairing_failed_no_active_window
import babymonitor.appcommon.generated.resources.client_pairing_failed_server_not_on_network
import babymonitor.appcommon.generated.resources.client_pairing_failed_wrong_pin
import babymonitor.appcommon.generated.resources.client_pairing_qr_wrong_device
import babymonitor.appcommon.generated.resources.client_pairing_server_not_on_network
import babymonitor.appcommon.generated.resources.pairing_progress
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.vpilo.babymonitor.camera.presentation.pairing.CameraQrScanner
import org.vpilo.babymonitor.model.repository.PairingFailureCause
import org.vpilo.babymonitor.model.repository.PairingOutcome
import org.vpilo.babymonitor.presentation.SURFACE_ALPHA
import org.vpilo.babymonitor.presentation.Theme
import org.vpilo.babymonitor.presentation.composables.AppDestination
import org.vpilo.babymonitor.presentation.composables.Backdrop

@Composable
fun ClientPairingScreen(
    modifier: Modifier = Modifier,
    viewModel: ClientPairingScreenViewModel,
    onPaired: () -> Unit,
    onBackClicked: () -> Unit,
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel.effectsFlow) {
        viewModel.effectsFlow.collect { effect ->
            when (effect) {
                ClientPairingScreenEffect.Paired -> onPaired()
            }
        }
    }

    AppDestination(
        modifier = modifier,
        title = stringResource(Res.string.app_title_client_pairing, state.server?.name.orEmpty()),
        onMainActionClicked = onBackClicked,
    ) {
        val server = state.server
        if (server == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(Theme.Paddings.Medium),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(Res.string.client_pairing_server_not_on_network),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            return@AppDestination
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(Theme.Paddings.Medium),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Theme.Paddings.Medium, Alignment.CenterVertically),
        ) {
            CameraQrScanner(
                modifier = Modifier.border(1.dp, color = Color.Red),
                viewModel = koinViewModel(),
                onQrRead = { qrContent ->
                    viewModel.send(ClientPairingScreenAction.SubmitPin(qrContent, null))
                },
                onError = {
                    // VALERIO what to report?
                },
            )
            PinEntry(
                modifier = Modifier.border(1.dp, color = Color.Red),
                onPinEntered = { pin, device ->
                    viewModel.send(ClientPairingScreenAction.SubmitPin(pin, device))
                },
            )

            (state.outcome as? PairingOutcome.Failure)?.let { failure ->
                Text(
                    text =
                        stringResource(
                            when (failure.reason) {
                                PairingFailureCause.WRONG_PIN -> Res.string.client_pairing_failed_wrong_pin
                                PairingFailureCause.NO_ACTIVE_PAIRING_WINDOW -> Res.string.client_pairing_failed_no_active_window
                                PairingFailureCause.SERVER_NOT_ON_NETWORK -> Res.string.client_pairing_failed_server_not_on_network
                                PairingFailureCause.MITM_SUSPECTED -> Res.string.client_pairing_failed_mitm_suspected
                                PairingFailureCause.CONNECTION_FAILED -> Res.string.client_pairing_failed_connection_failed
                                PairingFailureCause.WRONG_DEVICE -> Res.string.client_pairing_qr_wrong_device
                            },
                        ),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (state.isPairing) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = SURFACE_ALPHA),
                ) {
                    Backdrop {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Theme.Paddings.Medium),
                        ) {
                            Text(stringResource(Res.string.pairing_progress))
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}
