package org.vpilo.babymonitor.app.client.pairing

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import babymonitor.appcommon.generated.resources.Res
import babymonitor.appcommon.generated.resources.app_title_client_pairing
import babymonitor.appcommon.generated.resources.client_pairing_camera_failure
import babymonitor.appcommon.generated.resources.client_pairing_camera_or_pin
import babymonitor.appcommon.generated.resources.client_pairing_failed_connection_failed
import babymonitor.appcommon.generated.resources.client_pairing_failed_invalid_qr
import babymonitor.appcommon.generated.resources.client_pairing_failed_mitm_suspected
import babymonitor.appcommon.generated.resources.client_pairing_failed_no_active_window
import babymonitor.appcommon.generated.resources.client_pairing_failed_server_not_on_network
import babymonitor.appcommon.generated.resources.client_pairing_failed_wrong_pin
import babymonitor.appcommon.generated.resources.client_pairing_qr_wrong_device
import babymonitor.appcommon.generated.resources.client_pairing_server_not_on_network
import babymonitor.appcommon.generated.resources.pairing_progress
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.module.dsl.viewModelOf
import org.vpilo.babymonitor.camera.presentation.pairing.CameraQrScanner
import org.vpilo.babymonitor.camera.presentation.pairing.CameraQrScannerViewModel
import org.vpilo.babymonitor.model.repository.PairingFailureCause
import org.vpilo.babymonitor.model.repository.PairingState
import org.vpilo.babymonitor.presentation.AppPreviewTheme
import org.vpilo.babymonitor.presentation.Theme
import org.vpilo.babymonitor.presentation.composables.AppDestination
import org.vpilo.babymonitor.presentation.snackbar.LocalSnackbarController

@Composable
fun ClientPairingScreen(
    modifier: Modifier = Modifier,
    viewModel: ClientPairingScreenViewModel,
    onPaired: () -> Unit,
    onBackClicked: () -> Unit,
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val snackbarController = LocalSnackbarController.current
    var isCameraAvailable by remember { mutableStateOf(true) }

    LaunchedEffect(viewModel.effectsFlow) {
        viewModel.effectsFlow.collect { effect ->
            when (effect) {
                ClientPairingScreenEffect.Paired -> {
                    onPaired()
                }

                ClientPairingScreenEffect.ServerUnavailable -> {
                    snackbarController.show(message = getString(Res.string.client_pairing_server_not_on_network))
                    onBackClicked()
                }
            }
        }
    }

    AppDestination(
        modifier = modifier,
        title = stringResource(Res.string.app_title_client_pairing, state.server?.name.orEmpty()),
        onMainActionClicked = onBackClicked,
    ) {
        ClientPairingView(
            modifier = Modifier.fillMaxSize(),
            serverName = state.server?.name.orEmpty(),
            pairingState = state.pairingState,
            isCameraAvailable = isCameraAvailable,
            pairingPinLength = viewModel.pairingPinLength,
            onQrRead = { qrContent ->
                viewModel.send(ClientPairingScreenAction.SubmitQr(qrContent))
            },
            onPinEntered = { pin ->
                viewModel.send(ClientPairingScreenAction.SubmitPin(pin))
            },
            onCameraError = {
                isCameraAvailable = false
            },
        )
    }
}

@Composable
private fun ClientPairingView(
    modifier: Modifier = Modifier,
    serverName: String,
    pairingState: PairingState,
    isCameraAvailable: Boolean = true,
    pairingPinLength: Int = 3,
    onQrRead: (qrContent: String) -> Unit = {},
    onPinEntered: (pin: String) -> Unit = {},
    onCameraError: () -> Unit = {},
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            modifier =
                Modifier
                    .padding(vertical = Theme.Paddings.Medium),
            text = stringResource(Res.string.client_pairing_camera_or_pin, serverName),
            style = MaterialTheme.typography.bodyMedium,
        )
        PinEntry(
            modifier =
                Modifier
                    .padding(Theme.Paddings.Medium),
            pairingPinLength = pairingPinLength,
            onPinEntered = onPinEntered,
            pinResetKey = pairingState,
        )

        val statusStringResource =
            when (pairingState) {
                is PairingState.InProgress -> {
                    Res.string.pairing_progress
                }

                is PairingState.Failure -> {
                    when (pairingState.reason) {
                        PairingFailureCause.INVALID_QR -> Res.string.client_pairing_failed_invalid_qr
                        PairingFailureCause.WRONG_PIN -> Res.string.client_pairing_failed_wrong_pin
                        PairingFailureCause.NO_ACTIVE_PAIRING_WINDOW -> Res.string.client_pairing_failed_no_active_window
                        PairingFailureCause.SERVER_NOT_ON_NETWORK -> Res.string.client_pairing_failed_server_not_on_network
                        PairingFailureCause.MITM_SUSPECTED -> Res.string.client_pairing_failed_mitm_suspected
                        PairingFailureCause.CONNECTION_FAILED -> Res.string.client_pairing_failed_connection_failed
                        PairingFailureCause.WRONG_DEVICE -> Res.string.client_pairing_qr_wrong_device
                    }
                }

                else -> {
                    null
                }
            }
        Text(
            modifier = Modifier.padding(bottom = Theme.Paddings.Medium),
            text = statusStringResource?.let { stringResource(it) }.orEmpty(),
            color = if (pairingState is PairingState.Failure) MaterialTheme.colorScheme.error else Color.Unspecified,
            style = MaterialTheme.typography.bodyLarge,
        )

        val cameraShape = RoundedCornerShape(Theme.Borders.Rounded)
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .weight(weight = .75f)
                    .fillMaxWidth()
                    .padding(Theme.Paddings.Medium)
                    .clip(cameraShape)
                    .border(
                        width = Theme.Borders.Thin,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        shape = cameraShape,
                    ),
        ) {
            if (isCameraAvailable) {
                CameraQrScanner(
                    modifier = Modifier,
                    viewModel = koinViewModel(),
                    onQrRead = onQrRead,
                    onError = onCameraError,
                )
            } else {
                Text(
                    modifier = Modifier.padding(Theme.Paddings.Medium),
                    text = stringResource(Res.string.client_pairing_camera_failure),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Preview
@Composable
private fun ClientPairingViewNormalPreview() {
    AppPreviewTheme(
        withModule = {
            viewModelOf(::CameraQrScannerViewModel)
        },
    ) {
        ClientPairingView(
            pairingState = PairingState.Waiting,
            serverName = "Baby room",
            pairingPinLength = 3,
        )
    }
}

@Preview
@Composable
private fun ClientPairingViewErrorPreview() {
    AppPreviewTheme(
        withModule = {
            viewModelOf(::CameraQrScannerViewModel)
        },
    ) {
        ClientPairingView(
            pairingState = PairingState.Failure(PairingFailureCause.NO_ACTIVE_PAIRING_WINDOW),
            serverName = "Baby room",
            pairingPinLength = 3,
        )
    }
}

@Preview
@Composable
private fun ClientPairingViewNoCameraPreview() {
    AppPreviewTheme(
        withModule = {
            viewModelOf(::CameraQrScannerViewModel)
        },
    ) {
        ClientPairingView(
            pairingState = PairingState.Failure(PairingFailureCause.WRONG_PIN),
            serverName = "Baby room",
            isCameraAvailable = false,
            pairingPinLength = 3,
        )
    }
}

@Preview
@Composable
private fun ClientPairingViewPairingPreview() {
    AppPreviewTheme(
        withModule = {
            viewModelOf(::CameraQrScannerViewModel)
        },
    ) {
        ClientPairingView(
            pairingState = PairingState.InProgress,
            serverName = "Baby room",
            pairingPinLength = 3,
        )
    }
}
