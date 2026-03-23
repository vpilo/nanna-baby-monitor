package org.vpilo.babymonitor.app.clientconnectionchooser

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import babymonitor.appcommon.generated.resources.Res
import babymonitor.appcommon.generated.resources.client_connection_chooser_choose
import babymonitor.appcommon.generated.resources.client_connection_chooser_client_quit
import babymonitor.appcommon.generated.resources.client_connection_chooser_connected
import babymonitor.appcommon.generated.resources.client_connection_chooser_connecting
import babymonitor.appcommon.generated.resources.client_connection_chooser_server_not_found
import babymonitor.appcommon.generated.resources.client_connection_chooser_server_quit
import babymonitor.appcommon.generated.resources.client_connection_chooser_unknown_error
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.vpilo.babymonitor.model.repository.NetworkState
import org.vpilo.babymonitor.presentation.AppTheme
import org.vpilo.babymonitor.presentation.Theme
import org.vpilo.babymonitor.presentation.composables.BackButton
import java.net.InetAddress

@Composable
fun ClientConnectionChooserScreen(
    modifier: Modifier = Modifier,
    viewModel: ClientConnectionChooserViewModel,
    onConnected: (serverAddress: InetAddress) -> Unit,
    onBackClicked: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.connectedEvents.collect { address ->
            onConnected(address)
        }
    }

    ClientConnectionChooserContent(
        modifier = modifier.fillMaxSize(),
        networkState = state.networkState,
        servers = state.availableServers,
        onConnectRequested = { viewModel.onAction(ClientConnectionChooserAction.ConnectToServer(it)) },
        onBackClicked = onBackClicked,
    )
}

@Composable
private fun ClientConnectionChooserContent(
    modifier: Modifier = Modifier,
    networkState: NetworkState,
    servers: Set<InetAddress>,
    onConnectRequested: (serverName: InetAddress) -> Unit,
    onBackClicked: () -> Unit,
) {
    val lazyListState = rememberLazyListState()

    Column(modifier = modifier) {
        BackButton(
            modifier = Modifier,
            onBackClicked = onBackClicked,
        )

        val label: StringResource
        var argument: String? = null
        var labelColor: Color = Theme.Colors.text

        when (networkState) {
            is NetworkState.Connecting -> {
                label = Res.string.client_connection_chooser_connecting
            }

            is NetworkState.Connected -> {
                label = Res.string.client_connection_chooser_connected
                argument = networkState.address.hostAddress
            }

            is NetworkState.Disconnected ->
                when (networkState.reason) {
                    NetworkState.ErrorReason.NotConnectedYet -> {
                        label = Res.string.client_connection_chooser_choose
                    }

                    NetworkState.ErrorReason.ServerNotFound -> {
                        label = Res.string.client_connection_chooser_server_not_found
                        labelColor = Theme.Colors.error
                    }

                    NetworkState.ErrorReason.ServerQuit -> {
                        label = Res.string.client_connection_chooser_server_quit
                        labelColor = Theme.Colors.error
                    }

                    NetworkState.ErrorReason.ClientQuit -> {
                        label = Res.string.client_connection_chooser_client_quit
                    }

                    else -> {
                        label = Res.string.client_connection_chooser_unknown_error
                        labelColor = Theme.Colors.error
                    }
                }
        }
        Text(
            text = argument?.let { stringResource(label, argument) } ?: stringResource(label),
            style = MaterialTheme.typography.bodyMedium,
            color = labelColor,
        )

        (networkState as? NetworkState.Disconnected)
            ?.additionalInfo
            ?.let { exception ->
                Text(
                    text = "Error details: ${exception.localizedMessage}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Theme.Colors.error,
                )
            }

        LazyColumn(state = lazyListState, modifier = Modifier) {
            servers.forEach {
                item {
                    Button(
                        onClick = { onConnectRequested(it) },
                        modifier = Modifier,
                    ) {
                        Text(
                            text = it.hostAddress,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Theme.Colors.text,
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun ClientConnectionChooserScreenPreview() {
    AppTheme {
        ClientConnectionChooserContent(
            networkState = NetworkState.Disconnected(NetworkState.ErrorReason.NotConnectedYet),
            servers = setOf(InetAddress.getLoopbackAddress(), InetAddress.getByName("1.2.3.4")),
            onConnectRequested = {},
            onBackClicked = {},
        )
    }
}
