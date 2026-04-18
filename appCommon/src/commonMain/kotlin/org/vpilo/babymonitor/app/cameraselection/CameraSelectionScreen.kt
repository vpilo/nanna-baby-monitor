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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import babymonitor.appcommon.generated.resources.Res
import babymonitor.appcommon.generated.resources.app_title_client_connect
import babymonitor.appcommon.generated.resources.client_connection_chooser_choose
import babymonitor.appcommon.generated.resources.client_connection_chooser_client_quit
import babymonitor.appcommon.generated.resources.client_connection_chooser_connected
import babymonitor.appcommon.generated.resources.client_connection_chooser_connecting
import babymonitor.appcommon.generated.resources.client_connection_chooser_no_servers_found
import babymonitor.appcommon.generated.resources.client_connection_chooser_server_not_found
import babymonitor.appcommon.generated.resources.client_connection_chooser_server_quit
import babymonitor.appcommon.generated.resources.client_connection_chooser_unknown_error
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.vpilo.babymonitor.model.repository.NetworkState
import org.vpilo.babymonitor.model.repository.ServerId
import org.vpilo.babymonitor.presentation.AppPreviewTheme
import org.vpilo.babymonitor.presentation.Theme
import org.vpilo.babymonitor.presentation.composables.AppDestination
import org.vpilo.babymonitor.presentation.composables.LoadingBox
import org.vpilo.babymonitor.presentation.composables.LoadingIcon

@Composable
fun CameraSelectionScreen(
    modifier: Modifier = Modifier,
    viewModel: CameraSelectionScreenViewModel,
    onConnected: () -> Unit,
    onBackClicked: () -> Unit,
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effectsFlow.collect { effect ->
            when (effect) {
                is CameraSelectionScreenEffect.Connected -> onConnected()
            }
        }
    }

    AppDestination(
        title = Res.string.app_title_client_connect,
        onMainActionClicked = onBackClicked,
    ) {
        CameraSelectionScreenContent(
            modifier = modifier.fillMaxSize(),
            networkState = state.networkState,
            localServers = state.localServers,
            relayServers = state.relayServers,
            onConnectRequested = { viewModel.send(CameraSelectionScreenAction.ConnectToServer(it)) },
        )
    }
}

@Composable
private fun CameraSelectionScreenContent(
    modifier: Modifier = Modifier,
    networkState: NetworkState,
    localServers: Set<ServerId>,
    relayServers: Set<ServerId>,
    onConnectRequested: (server: ServerId) -> Unit,
) {
    val lazyListState = rememberLazyListState()

    Column(modifier = modifier) {
        InfoLabel(networkState)
        Spacer(modifier = Modifier.size(Theme.Paddings.Medium))

        (networkState as? NetworkState.Disconnected)
            ?.additionalInfo
            ?.let { exception ->
                Text(
                    text = "Error details: ${exception.localizedMessage}",
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
            if (localServers.isEmpty() && relayServers.isEmpty()) {
                item {
                    Text(
                        text = stringResource(Res.string.client_connection_chooser_no_servers_found),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    LoadingBox()
                }
            }

            if (localServers.isNotEmpty()) {
                item {
                    Text(
                        text = "Local",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Theme.Paddings.Small),
                    )
                }
                items(items = localServers.toList()) { server ->
                    ServerButton(server, networkState, onConnectRequested)
                    Spacer(modifier = Modifier.size(Theme.Paddings.Tiny))
                }
            }

            if (relayServers.isNotEmpty()) {
                item {
                    Text(
                        text = "Remote",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Theme.Paddings.Small),
                    )
                }
                items(items = relayServers.toList()) { server ->
                    ServerButton(server, networkState, onConnectRequested)
                    Spacer(modifier = Modifier.size(Theme.Paddings.Tiny))
                }
            }
        }
    }
}

@Composable
private fun ServerButton(
    server: ServerId,
    networkState: NetworkState,
    onConnectRequested: (ServerId) -> Unit,
) {
    Button(
        enabled = networkState !is NetworkState.Connecting,
        onClick = { onConnectRequested(server) },
    ) {
        Text(
            modifier = Modifier.padding(Theme.Paddings.Small),
            text = server.name,
            style = MaterialTheme.typography.bodyMedium,
        )
        if ((networkState as? NetworkState.Connecting)?.server == server) {
            LoadingIcon()
        }
    }
}

@Composable
private fun InfoLabel(networkState: NetworkState) {
    val label: StringResource
    var argument: String? = null
    var labelColor: Color = MaterialTheme.colorScheme.onBackground

    when (networkState) {
        is NetworkState.Connecting -> {
            label = Res.string.client_connection_chooser_connecting
        }

        is NetworkState.Connected -> {
            label = Res.string.client_connection_chooser_connected
            argument = networkState.server.name
        }

        is NetworkState.Disconnected -> {
            when (networkState.reason) {
                NetworkState.ErrorReason.NotConnectedYet -> {
                    label = Res.string.client_connection_chooser_choose
                }

                NetworkState.ErrorReason.ServerNotFound -> {
                    label = Res.string.client_connection_chooser_server_not_found
                    labelColor = MaterialTheme.colorScheme.error
                }

                NetworkState.ErrorReason.ServerQuit -> {
                    label = Res.string.client_connection_chooser_server_quit
                    labelColor = MaterialTheme.colorScheme.error
                }

                NetworkState.ErrorReason.ClientQuit -> {
                    label = Res.string.client_connection_chooser_client_quit
                }

                else -> {
                    label = Res.string.client_connection_chooser_unknown_error
                    labelColor = MaterialTheme.colorScheme.error
                }
            }
        }
    }
    Text(
        text = argument?.let { stringResource(label, argument) } ?: stringResource(label),
        style = MaterialTheme.typography.bodyMedium,
        color = labelColor,
    )
}

@Preview
@Composable
private fun CameraSelectionScreenPreview() =
    AppPreviewTheme {
        CameraSelectionScreenContent(
            networkState = NetworkState.Disconnected(NetworkState.ErrorReason.NotConnectedYet),
            localServers = setOf(ServerId("Baby Monitor-1234"), ServerId("Bedroom Camera")),
            relayServers = emptySet(),
            onConnectRequested = {},
        )
    }

@Preview
@Composable
private fun CameraSelectionScreenConnectingPreview() =
    AppPreviewTheme {
        CameraSelectionScreenContent(
            networkState = NetworkState.Connecting(ServerId("Bedroom Camera")),
            localServers = setOf(ServerId("Baby Monitor-1234"), ServerId("Bedroom Camera")),
            relayServers = emptySet(),
            onConnectRequested = {},
        )
    }

@Preview
@Composable
private fun CameraSelectionScreenNoServersPreview() =
    AppPreviewTheme {
        CameraSelectionScreenContent(
            networkState = NetworkState.Disconnected(NetworkState.ErrorReason.NotConnectedYet),
            localServers = emptySet(),
            relayServers = emptySet(),
            onConnectRequested = {},
        )
    }
