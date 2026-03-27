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
import org.vpilo.babymonitor.presentation.AppTheme
import org.vpilo.babymonitor.presentation.Theme
import org.vpilo.babymonitor.presentation.composables.LoadingBox
import org.vpilo.babymonitor.presentation.composables.LoadingIcon
import java.net.InetAddress

@Composable
fun CameraSelectionScreen(
    modifier: Modifier = Modifier,
    viewModel: CameraSelectionScreenViewModel,
    onConnected: (serverAddress: InetAddress) -> Unit,
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effectsFlow.collect { effect ->
            when (effect) {
                is CameraSelectionScreenEffect.Connected -> onConnected(effect.address)
            }
        }
    }

    CameraSelectionScreenContent(
        modifier = modifier.fillMaxSize(),
        networkState = state.networkState,
        servers = state.availableServers,
        onConnectRequested = { viewModel.send(CameraSelectionScreenAction.ConnectToServer(it)) },
    )
}

@Composable
private fun CameraSelectionScreenContent(
    modifier: Modifier = Modifier,
    networkState: NetworkState,
    servers: Set<InetAddress>,
    onConnectRequested: (serverName: InetAddress) -> Unit,
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
            if (servers.isEmpty()) {
                item {
                    Text(
                        text = stringResource(Res.string.client_connection_chooser_no_servers_found),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    LoadingBox()
                }
            }
            items(items = servers.toList()) { server ->
                Button(
                    enabled = networkState !is NetworkState.Connecting,
                    onClick = { onConnectRequested(server) },
                    modifier = Modifier,
                ) {
                    Text(
                        modifier = Modifier.padding(Theme.Paddings.Small),
                        text = server.hostAddress ?: server.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if ((networkState as? NetworkState.Connecting)?.address == server) {
                        LoadingIcon()
                    }
                }
                Spacer(modifier = Modifier.size(Theme.Paddings.Tiny))
            }
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
            argument = networkState.address.hostAddress
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
    AppTheme {
        CameraSelectionScreenContent(
            networkState = NetworkState.Disconnected(NetworkState.ErrorReason.NotConnectedYet),
            servers = setOf(InetAddress.getLoopbackAddress(), InetAddress.getByName("1.2.3.4")),
            onConnectRequested = {},
        )
    }

@Preview
@Composable
private fun CameraSelectionScreenConnectingPreview() =
    AppTheme {
        CameraSelectionScreenContent(
            networkState = NetworkState.Connecting(InetAddress.getLoopbackAddress()),
            servers = setOf(InetAddress.getLoopbackAddress(), InetAddress.getByName("1.2.3.4")),
            onConnectRequested = {},
        )
    }

@Preview
@Composable
private fun CameraSelectionScreenNoServersPreview() =
    AppTheme {
        CameraSelectionScreenContent(
            networkState = NetworkState.Disconnected(NetworkState.ErrorReason.NotConnectedYet),
            servers = emptySet(),
            onConnectRequested = {},
        )
    }
