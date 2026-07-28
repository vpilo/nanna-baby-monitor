package org.vpilo.babymonitor.app.cameraselection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import babymonitor.appcommon.generated.resources.client_connection_chooser_connected
import babymonitor.appcommon.generated.resources.client_connection_chooser_connecting
import babymonitor.appcommon.generated.resources.client_connection_chooser_no_servers_found
import babymonitor.appcommon.generated.resources.client_connection_chooser_pairing_revoked
import babymonitor.appcommon.generated.resources.client_connection_chooser_reconnecting
import babymonitor.appcommon.generated.resources.client_connection_chooser_server_not_found
import babymonitor.appcommon.generated.resources.client_connection_chooser_server_quit
import babymonitor.appcommon.generated.resources.client_connection_chooser_servers_section_connectable
import babymonitor.appcommon.generated.resources.client_connection_chooser_servers_section_new
import babymonitor.appcommon.generated.resources.client_connection_chooser_servers_section_paired
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
    val connectableServers = remember(state) { state.connectableServers }
    val pairedServers = remember(state) { state.pairedServers }
    val newServers = remember(state) { state.newServers }
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
            connectableServers = connectableServers,
            pairedServers = pairedServers,
            newServers = newServers,
            onConnectRequested = { viewModel.send(CameraSelectionScreenAction.ConnectToServer(it)) },
        )
    }
}

@Composable
private fun CameraSelectionScreenContent(
    modifier: Modifier = Modifier,
    connectionState: ConnectionState,
    connectableServers: List<Device.Server> = emptyList(),
    pairedServers: List<Device.Server> = emptyList(),
    newServers: List<Device.Server> = emptyList(),
    onConnectRequested: (server: Device.Server) -> Unit,
) {
    val lazyListState = rememberLazyListState()

    Column(modifier = modifier) {
        Text(
            text = stringResource(Res.string.client_connection_chooser_choose),
            style = MaterialTheme.typography.bodyMedium,
        )

        (connectionState as? ConnectionState.Disconnected)
            ?.additionalInfo
            ?.let { exception ->
                Text(
                    text = "Error details: ${exception.prettify()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

        if (connectableServers.isEmpty() && pairedServers.isEmpty() && newServers.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    modifier
                        .fillMaxSize(),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(Res.string.client_connection_chooser_no_servers_found),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    LoadingIcon()
                }
            }
            return
        }

        LazyColumn(
            state = lazyListState,
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(Theme.Paddings.Small),
            modifier =
                Modifier
                    .padding(horizontal = Theme.Paddings.Medium)
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally),
        ) {
            if (connectableServers.isNotEmpty()) {
                stickyHeader {
                    Header(label = Res.string.client_connection_chooser_servers_section_connectable)
                }
                items(items = connectableServers, key = { it.name }) { server ->
                    Server(
                        name = server.name,
                        isRemote = server is Device.RemoteServer,
                        isEnabled = connectionState !is ConnectionState.Connecting,
                        isConnecting = (connectionState as? ConnectionState.Connecting)?.server == server,
                        onClick = { onConnectRequested(server) },
                    )
                }
            }
            if (pairedServers.isNotEmpty()) {
                stickyHeader {
                    Header(label = Res.string.client_connection_chooser_servers_section_paired)
                }
                items(items = pairedServers, key = { it.name }) { server ->
                    Server(
                        name = server.name,
                        isRemote = server is Device.RemoteServer,
                        isEnabled = false,
                        isConnecting = (connectionState as? ConnectionState.Connecting)?.server == server,
                        onClick = {},
                    )
                }
            }
            if (newServers.isNotEmpty()) {
                stickyHeader {
                    Header(label = Res.string.client_connection_chooser_servers_section_new)
                }
                items(items = newServers, key = { it.name }) { server ->
                    Server(
                        name = server.name,
                        isRemote = server is Device.RemoteServer,
                        isEnabled = connectionState !is ConnectionState.Connecting,
                        isConnecting = (connectionState as? ConnectionState.Connecting)?.server == server,
                        onClick = { onConnectRequested(server) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(
    modifier: Modifier = Modifier,
    label: StringResource,
) {
    Text(
        modifier =
            modifier
                .padding(
                    start = Theme.Paddings.Small,
                    end = Theme.Paddings.Small,
                    bottom = Theme.Paddings.Tiny,
                    top = Theme.Paddings.Large,
                ),
        text = stringResource(label),
        style = MaterialTheme.typography.titleLarge,
    )
}

@Composable
private fun Server(
    modifier: Modifier = Modifier,
    name: String,
    isRemote: Boolean,
    isEnabled: Boolean,
    isConnecting: Boolean,
    onClick: () -> Unit,
) {
    val serverTypeTooltip =
        if (isRemote) {
            Res.string.camera_selection_server_type_relay
        } else {
            Res.string.camera_selection_server_type_local
        }

    Tooltip(text = stringResource(serverTypeTooltip)) {
        Card(
            modifier = modifier.fillMaxWidth(),
            enabled = isEnabled,
            onClick = onClick,
            border = CardDefaults.outlinedCardBorder(),
            colors = CardDefaults.elevatedCardColors(),
            elevation = CardDefaults.elevatedCardElevation(),
            shape = CardDefaults.elevatedShape,
        ) {
            Row(
                modifier = Modifier.padding(Theme.Paddings.Large),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (isRemote) {
                    Icon(
                        modifier = Modifier.padding(start = Theme.Paddings.Small),
                        imageVector = Icons.Default.Cloud,
                        contentDescription = null,
                    )
                }
                if (isConnecting) {
                    LoadingIcon(modifier = Modifier.size(Theme.Sizes.IconSmall))
                }
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
        is ConnectionState.Connecting -> {
            label = Res.string.client_connection_chooser_connecting
            deviceName = connectionState.server.name
        }

        is ConnectionState.Reconnecting -> {
            label = Res.string.client_connection_chooser_reconnecting
            deviceName = connectionState.server.name
        }

        is ConnectionState.Connected -> {
            label = Res.string.client_connection_chooser_connected
            deviceName = connectionState.server.name
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
    AppPreviewTheme(
        modifier = Modifier.fillMaxSize(),
    ) {
        CameraSelectionScreenContent(
            connectionState = ConnectionState.Disconnected(ConnectionState.ErrorReason.NotConnectedYet),
            connectableServers =
                listOf(
                    makePreviewServer("Baby Monitor 1234, paired and visible", isLocal = false),
                    makePreviewServer("Bedroom camera, paired and visible"),
                ),
            pairedServers =
                listOf(
                    makePreviewServer("Remote Cam, paired", isLocal = false),
                    makePreviewServer("Living room camera, paired"),
                ),
            newServers =
                listOf(
                    makePreviewServer("Toilet Cam, unpaired", isLocal = false),
                    makePreviewServer("Pigeon nest, unpaired"),
                ),
            onConnectRequested = {},
        )
    }

@Preview
@Composable
private fun CameraSelectionScreenConnectingPreview() =
    AppPreviewTheme {
        val server = makePreviewServer("Baby Monitor 1234, paired and visible")
        CameraSelectionScreenContent(
            connectionState = ConnectionState.Connecting(server),
            connectableServers =
                listOf(
                    server,
                    makePreviewServer("Bedroom camera, paired and visible"),
                ),
            onConnectRequested = {},
        )
    }

@Preview
@Composable
private fun CameraSelectionScreenEmptyPreview() =
    AppPreviewTheme {
        CameraSelectionScreenContent(
            connectionState = ConnectionState.Disconnected(ConnectionState.ErrorReason.NotConnectedYet),
            onConnectRequested = {},
        )
    }
