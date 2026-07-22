package org.vpilo.babymonitor.app.client.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import babymonitor.appcommon.generated.resources.Res
import babymonitor.appcommon.generated.resources.app_title_client_home
import babymonitor.appcommon.generated.resources.app_title_client_home_name
import babymonitor.appcommon.generated.resources.camera_selection_server_type_relay
import babymonitor.appcommon.generated.resources.client_disconnect
import babymonitor.appcommon.generated.resources.client_reconnecting
import org.jetbrains.compose.resources.stringResource
import org.vpilo.babymonitor.camera.presentation.composables.PanningVideoFeed
import org.vpilo.babymonitor.model.CaptureMode
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.model.OpaqueVideoStream
import org.vpilo.babymonitor.model.repository.ConnectionState
import org.vpilo.babymonitor.model.repository.DEVICE_STATE_DATA_UNAVAILABLE
import org.vpilo.babymonitor.presentation.AppPreviewTheme
import org.vpilo.babymonitor.presentation.SURFACE_ALPHA
import org.vpilo.babymonitor.presentation.Theme
import org.vpilo.babymonitor.presentation.client.BatteryState
import org.vpilo.babymonitor.presentation.client.SignalState
import org.vpilo.babymonitor.presentation.composables.AppDestination
import org.vpilo.babymonitor.presentation.composables.AppDestinationMainAction
import org.vpilo.babymonitor.presentation.composables.Backdrop
import org.vpilo.babymonitor.presentation.composables.Tooltip
import org.vpilo.babymonitor.presentation.preview.makePreviewServer
import org.vpilo.babymonitor.presentation.preview.makePreviewVideoStream

@Composable
fun ClientHomeScreen(
    modifier: Modifier = Modifier,
    viewModel: ClientHomeScreenViewModel,
    onDisconnected: () -> Unit,
    onMenuClicked: () -> Unit,
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val videoStream by viewModel.videoStreamFlow.collectAsState(initial = null)

    LaunchedEffect(viewModel.effectsFlow) {
        viewModel.effectsFlow.collect { effect ->
            when (effect) {
                is ClientHomeScreenEffect.Disconnected -> onDisconnected()
            }
        }
    }

    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = true,
        onBackCancelled = { /* no-op */ },
        onBackCompleted = { onMenuClicked() },
    )

    val isRemoteServer: Boolean
    val title: String
    with(state.connectionState) {
        if (this is ConnectionState.Connected) {
            isRemoteServer = server is Device.RemoteServer
            title = stringResource(Res.string.app_title_client_home_name, server.name)
        } else {
            isRemoteServer = false
            title = stringResource(Res.string.app_title_client_home)
        }
    }

    AppDestination(
        modifier = modifier.fillMaxSize(),
        title = title,
        mainAction = AppDestinationMainAction.Menu,
        onMainActionClicked = {
            onMenuClicked()
        },
        actions = {
            VideoFeedControlButton(
                modifier = Modifier.padding(Theme.Paddings.Tiny),
                canPlay = state.captureMode != CaptureMode.AUDIO_ONLY,
                isPlaying = state.isVideoPlaying,
                onToggle = { viewModel.send(ClientHomeScreenAction.ToggleVideo) },
            )
            AudioFeedControlButton(
                modifier = Modifier.padding(Theme.Paddings.Tiny),
                canPlay = state.captureMode != CaptureMode.VIDEO_ONLY,
                isPlaying = state.isAudioPlaying,
                onToggle = { viewModel.send(ClientHomeScreenAction.ToggleAudio) },
            )
        },
    ) {
        ClientHomeScreenContent(
            modifier = Modifier.fillMaxSize(),
            videoStream = videoStream,
            captureMode = state.captureMode,
            batteryLevel = state.batteryLevel,
            signalQuality = state.signalQuality,
            connectionState = state.connectionState,
            onDisconnected = { viewModel.send(ClientHomeScreenAction.Disconnect) },
            isRemoteServer = isRemoteServer,
        )
    }
}

@Composable
private fun ClientHomeScreenContent(
    modifier: Modifier = Modifier,
    videoStream: OpaqueVideoStream?,
    captureMode: CaptureMode,
    batteryLevel: Int,
    signalQuality: Int,
    connectionState: ConnectionState,
    onDisconnected: () -> Unit,
    isRemoteServer: Boolean,
) {
    Box(modifier = modifier) {
        PanningVideoFeed(videoStream = videoStream, captureMode = captureMode)
        if (batteryLevel != DEVICE_STATE_DATA_UNAVAILABLE || signalQuality != DEVICE_STATE_DATA_UNAVAILABLE) {
            Backdrop(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(Theme.Paddings.Tiny),
            ) {
                BatteryState(modifier = Modifier.padding(start = Theme.Paddings.Tiny), batteryLevel = batteryLevel)
                SignalState(modifier = Modifier.padding(start = Theme.Paddings.Tiny), signalQuality = signalQuality)
                if (isRemoteServer) {
                    Tooltip(text = stringResource(Res.string.camera_selection_server_type_relay)) {
                        Icon(
                            modifier = Modifier.padding(start = Theme.Paddings.Tiny),
                            imageVector = Icons.Default.Cloud,
                            contentDescription = null,
                        )
                    }
                }
            }
        }

        if (connectionState is ConnectionState.Connecting || connectionState is ConnectionState.Reconnecting) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = SURFACE_ALPHA),
            ) {
                Backdrop {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(stringResource(Res.string.client_reconnecting))
                        Button(
                            modifier = Modifier.padding(top = Theme.Paddings.Medium),
                            onClick = onDisconnected,
                        ) {
                            Text(stringResource(Res.string.client_disconnect))
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun ClientHomeScreenPreview() =
    AppPreviewTheme {
        ClientHomeScreenContent(
            modifier = Modifier.fillMaxSize(),
            videoStream = makePreviewVideoStream(),
            captureMode = CaptureMode.AUDIO_AND_VIDEO,
            batteryLevel = 5,
            signalQuality = 3,
            connectionState = ConnectionState.Connected(makePreviewServer("Baby Monitor-1234")),
            onDisconnected = { },
            isRemoteServer = false,
        )
    }

@Preview
@Composable
private fun ClientHomeScreenVideoOnlyPreview() =
    AppPreviewTheme {
        ClientHomeScreenContent(
            modifier = Modifier.fillMaxSize(),
            videoStream = makePreviewVideoStream(),
            captureMode = CaptureMode.VIDEO_ONLY,
            batteryLevel = DEVICE_STATE_DATA_UNAVAILABLE,
            signalQuality = 93,
            connectionState = ConnectionState.Connected(makePreviewServer("Baby Monitor-1234")),
            onDisconnected = { },
            isRemoteServer = true,
        )
    }

@Preview
@Composable
private fun ClientHomeScreenNoSignalOrBatteryPreview() =
    AppPreviewTheme {
        ClientHomeScreenContent(
            modifier = Modifier.fillMaxSize(),
            videoStream = makePreviewVideoStream(),
            captureMode = CaptureMode.AUDIO_AND_VIDEO,
            batteryLevel = DEVICE_STATE_DATA_UNAVAILABLE,
            signalQuality = DEVICE_STATE_DATA_UNAVAILABLE,
            connectionState = ConnectionState.Connected(makePreviewServer("Baby Monitor-1234")),
            onDisconnected = { },
            isRemoteServer = false,
        )
    }

@Preview
@Composable
private fun ClientHomeScreenDisconnectedPreview() =
    AppPreviewTheme {
        ClientHomeScreenContent(
            modifier = Modifier.fillMaxSize(),
            videoStream = makePreviewVideoStream(),
            captureMode = CaptureMode.AUDIO_AND_VIDEO,
            batteryLevel = DEVICE_STATE_DATA_UNAVAILABLE,
            signalQuality = DEVICE_STATE_DATA_UNAVAILABLE,
            connectionState = ConnectionState.Disconnected(reason = ConnectionState.ErrorReason.ClientQuit),
            onDisconnected = { },
            isRemoteServer = false,
        )
    }
