package org.vpilo.babymonitor.app.clientpairing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import babymonitor.appcommon.generated.resources.Res
import babymonitor.appcommon.generated.resources.app_title_client_pairing
import babymonitor.appcommon.generated.resources.client_pairing_failed_connection_failed
import babymonitor.appcommon.generated.resources.client_pairing_failed_mitm_suspected
import babymonitor.appcommon.generated.resources.client_pairing_failed_no_active_window
import babymonitor.appcommon.generated.resources.client_pairing_failed_server_not_on_network
import babymonitor.appcommon.generated.resources.client_pairing_failed_wrong_pin
import babymonitor.appcommon.generated.resources.client_pairing_server_not_on_network
import org.jetbrains.compose.resources.stringResource
import org.vpilo.babymonitor.camera.presentation.pairing.PinEntrySection
import org.vpilo.babymonitor.model.repository.PairingFailureCause
import org.vpilo.babymonitor.model.repository.PairingOutcome
import org.vpilo.babymonitor.presentation.Theme
import org.vpilo.babymonitor.presentation.composables.AppDestination

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
            if (state.isPairing) {
                CircularProgressIndicator()
            } else {
                PinEntrySection(
                    modifier = Modifier,
                    expectedDeviceId = server.id.toString(),
                    onPinEntered = { viewModel.send(ClientPairingScreenAction.SubmitPin(it)) },
                )
            }

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
                            },
                        ),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
