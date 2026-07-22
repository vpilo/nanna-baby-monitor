package org.vpilo.babymonitor.app.cameraselection

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import babymonitor.appcommon.generated.resources.Res
import babymonitor.appcommon.generated.resources.app_title_client_connect
import babymonitor.appcommon.generated.resources.camera_selection_server_type_local
import babymonitor.appcommon.generated.resources.camera_selection_server_type_relay
import babymonitor.appcommon.generated.resources.client_connection_chooser_choose
import babymonitor.appcommon.generated.resources.client_connection_chooser_client_quit
import babymonitor.appcommon.generated.resources.client_connection_chooser_connecting
import babymonitor.appcommon.generated.resources.client_connection_chooser_no_servers_found
import babymonitor.appcommon.generated.resources.client_connection_chooser_pairing_revoked
import babymonitor.appcommon.generated.resources.client_connection_chooser_reconnecting
import babymonitor.appcommon.generated.resources.client_connection_chooser_server_not_found
import babymonitor.appcommon.generated.resources.client_connection_chooser_server_quit
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.vpilo.babymonitor.common.ktx.prettify
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.model.repository.ConnectionState
import org.vpilo.babymonitor.presentation.AppPreviewTheme
import org.vpilo.babymonitor.presentation.Theme
import org.vpilo.babymonitor.presentation.composables.AppDestination
import org.vpilo.babymonitor.presentation.composables.AppDestinationMainAction
import org.vpilo.babymonitor.presentation.composables.ConnectionStatusIcons
import org.vpilo.babymonitor.presentation.composables.LoadingBox
import org.vpilo.babymonitor.presentation.composables.LoadingIcon
import org.vpilo.babymonitor.presentation.composables.Tooltip
import org.vpilo.babymonitor.presentation.preview.makePreviewServer
import org.vpilo.babymonitor.presentation.snackbar.LocalSnackbarController

@Composable
fun CameraSelectionScreen(
    modifier: Modifier = Modifier,
    viewModel: CameraSelectionScreenViewModel,
    onConnected: () -> Unit,
    onRequirePairing: (deviceId: String) -> Unit,
    onMenuClicked: () -> Unit,
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val serversList = remember(state) { state.availableServers.filterIsInstance<Device.Server>() }
    val snackbarController = LocalSnackbarController.current

    LaunchedEffect(viewModel.effectsFlow) {
        viewModel.effectsFlow.collect { effect ->
            when (effect) {
                is CameraSelectionScreenEffect.Connected -> {
                    onConnected()
                }

                is CameraSelectionScreenEffect.ConnectToServer -> {
                    viewModel.send(CameraSelectionScreenAction.ConnectToServer(effect.server))
                }

                is CameraSelectionScreenEffect.AnnounceConnectionEvent -> {
                    val message =
                        getConnectionStateMessage(effect.state, state.lastConnectedDevice)
                            ?: return@collect
                    snackbarController.show(message = message)
                }

                is CameraSelectionScreenEffect.RequirePairing -> {
                    onRequirePairing(effect.server.id.toString())
                }
            }
        }
    }

    AppDestination(
        title = Res.string.app_title_client_connect,
        mainAction = AppDestinationMainAction.Menu,
        onMainActionClicked = onMenuClicked,
        actions = {
            ConnectionStatusIcons(
                hasRelay = state.isRelayConfigured,
                isOnLocalNetwork = state.isAvailableOnLocalNetwork,
                isOnRelay = state.isAvailableOnRelay,
            )
        },
    ) {
        CameraSelectionScreenContent(
            modifier =
                modifier
                    .fillMaxSize()
                    .padding(Theme.Paddings.Medium),
            connectionState = state.connectionState,
            servers = serversList,
            onConnectRequested = { viewModel.send(CameraSelectionScreenAction.ConnectToServer(it)) },
        )
    }
}

@Composable
private fun CameraSelectionScreenContent(
    modifier: Modifier = Modifier,
    connectionState: ConnectionState,
    servers: List<Device.Server>,
    onConnectRequested: (server: Device.Server) -> Unit,
) {
    val lazyListState = rememberLazyListState()

    Column(modifier = modifier) {
        Text(
            text = stringResource(Res.string.client_connection_chooser_choose),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.size(Theme.Paddings.Medium))

        (connectionState as? ConnectionState.Disconnected)
            ?.additionalInfo
            ?.let { exception ->
                Text(
                    text = "Error details: ${exception.prettify()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

        LazyColumn(
            state = lazyListState,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                Modifier
                    .fillMaxWidth(fraction = .75f)
                    .align(Alignment.CenterHorizontally),
        ) {
            if (servers.isEmpty()) {
                item {
                    Text(
                        text = stringResource(Res.string.client_connection_chooser_no_servers_found),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    LoadingBox()
                }
            }

            items(items = servers, key = { it.name }) { server ->
                val isRemote = server is Device.RemoteServer
                Tooltip(
                    text =
                        stringResource(
                            if (isRemote) {
                                Res.string.camera_selection_server_type_relay
                            } else {
                                Res.string.camera_selection_server_type_local
                            },
                        ),
                ) {
                    Button(
                        enabled = connectionState !is ConnectionState.Connecting,
                        onClick = { onConnectRequested(server) },
                    ) {
                        if (isRemote) {
                            Icon(imageVector = Icons.Default.Cloud, contentDescription = null)
                        }
                        Text(
                            modifier = Modifier.padding(Theme.Paddings.Small),
                            text = server.name,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if ((connectionState as? ConnectionState.Connecting)?.server == server) {
                            LoadingIcon()
                        }
                    }
                }
                Spacer(modifier = Modifier.size(Theme.Paddings.Tiny))
            }
        }
    }
}

private suspend fun getConnectionStateMessage(
    connectionState: ConnectionState,
    lastDevice: Device?,
): String? {
    val label: StringResource
    var deviceName: String? = lastDevice?.name

    when (connectionState) {
        is ConnectionState.Connecting,
            -> {
                label = Res.string.client_connection_chooser_connecting
                deviceName = connectionState.server.name
            }

        is ConnectionState.Reconnecting,
            -> {
                label = Res.string.client_connection_chooser_reconnecting
                deviceName = connectionState.server.name
            }

        is ConnectionState.Connected -> {
            return null
        }

        is ConnectionState.Disconnected -> {
            when (connectionState.reason) {
                ConnectionState.ErrorReason.NotConnectedYet -> {
                    return null
                }

                ConnectionState.ErrorReason.ServerNotFound -> {
                    label = Res.string.client_connection_chooser_server_not_found
                }

                ConnectionState.ErrorReason.ServerQuit -> {
                    label = Res.string.client_connection_chooser_server_quit
                }

                ConnectionState.ErrorReason.ClientQuit -> {
                    label = Res.string.client_connection_chooser_client_quit
                }

                ConnectionState.ErrorReason.PairingRevoked -> {
                    label = Res.string.client_connection_chooser_pairing_revoked
                }
            }
        }
    }
    return deviceName?.let { getString(label, deviceName) } ?: getString(label)
}

@Preview
@Composable
private fun CameraSelectionScreenPreview() =
    AppPreviewTheme {
        CameraSelectionScreenContent(
            connectionState = ConnectionState.Disconnected(ConnectionState.ErrorReason.NotConnectedYet),
            servers =
                listOf(
                    makePreviewServer("Baby Monitor-1234"),
                    makePreviewServer("Bedroom Camera"),
                    makePreviewServer("Remote Cam", isLocal = false),
                ),
            onConnectRequested = {},
        )
    }

@Preview
@Composable
private fun CameraSelectionScreenConnectingPreview() =
    AppPreviewTheme(modifier = Modifier.fillMaxSize()) {
        CameraSelectionScreenContent(
            connectionState = ConnectionState.Connecting(makePreviewServer("Bedroom Camera")),
            servers =
                listOf(
                    makePreviewServer("Baby Monitor-1234"),
                    makePreviewServer("Remote Cam", isLocal = false),
                ),
            onConnectRequested = {},
        )
    }

@Preview
@Composable
private fun CameraSelectionScreenRevokedPreview() =
    AppPreviewTheme {
        CameraSelectionScreenContent(
            connectionState = ConnectionState.Disconnected(ConnectionState.ErrorReason.PairingRevoked),
            servers = emptyList(),
            onConnectRequested = {},
        )
    }
