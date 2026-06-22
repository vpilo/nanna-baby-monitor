package org.vpilo.babymonitor.app.client.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import babymonitor.appcommon.generated.resources.client_disconnect
import babymonitor.appcommon.generated.resources.client_reconnecting
import org.jetbrains.compose.resources.stringResource
import org.vpilo.babymonitor.camera.presentation.composables.PanningVideoFeed
import org.vpilo.babymonitor.model.CaptureMode
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

    val title =
        with(state.connectionState) {
            if (this is ConnectionState.Connected) {
                stringResource(Res.string.app_title_client_home_name, server.name)
            } else {
                stringResource(Res.string.app_title_client_home)
            }
        }

    AppDestination(
        modifier = modifier.fillMaxSize(),
        title = title,
        mainAction = AppDestinationMainAction.Menu,
        onMainActionClicked = {
            onMenuClicked()
        },
    ) {
        ClientHomeScreenContent(
            modifier = Modifier.fillMaxSize(),
            videoStream = videoStream,
            captureMode = state.captureMode,
            isAudioPlaying = state.isAudioPlaying,
            isVideoPlaying = state.isVideoPlaying,
            onToggleAudio = { viewModel.send(ClientHomeScreenAction.ToggleAudio) },
            onToggleVideo = { viewModel.send(ClientHomeScreenAction.ToggleVideo) },
            batteryLevel = state.batteryLevel,
            signalQuality = state.signalQuality,
            connectionState = state.connectionState,
            onDisconnected = onDisconnected,
        )
    }
}

@Composable
private fun ClientHomeScreenContent(
    modifier: Modifier = Modifier,
    videoStream: OpaqueVideoStream?,
    captureMode: CaptureMode,
    isAudioPlaying: Boolean,
    isVideoPlaying: Boolean,
    onToggleAudio: () -> Unit,
    onToggleVideo: () -> Unit,
    batteryLevel: Int,
    signalQuality: Int,
    connectionState: ConnectionState,
    onDisconnected: () -> Unit,
) {
    Box(modifier = modifier) {
        videoStream?.let { PanningVideoFeed(videoStream = it, captureMode = captureMode) }
        Row {
            VideoFeedControlButton(
                modifier = Modifier.padding(Theme.Paddings.Tiny),
                canPlay = captureMode != CaptureMode.AUDIO_ONLY,
                isPlaying = isVideoPlaying,
                onToggle = onToggleVideo,
            )
            AudioFeedControlButton(
                modifier = Modifier.padding(Theme.Paddings.Tiny),
                canPlay = captureMode != CaptureMode.VIDEO_ONLY,
                isPlaying = isAudioPlaying,
                onToggle = onToggleAudio,
            )
        }
        if (batteryLevel != DEVICE_STATE_DATA_UNAVAILABLE || signalQuality != DEVICE_STATE_DATA_UNAVAILABLE) {
            Backdrop(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(Theme.Paddings.Tiny),
            ) {
                BatteryState(batteryLevel = batteryLevel)
                SignalState(signalQuality = signalQuality)
            }
        }

        if (connectionState is ConnectionState.Reconnecting) {
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
            isAudioPlaying = false,
            isVideoPlaying = true,
            onToggleAudio = { },
            onToggleVideo = { },
            batteryLevel = 5,
            signalQuality = 3,
            connectionState = ConnectionState.Connected(makePreviewServer("Baby Monitor-1234")),
            onDisconnected = { },
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
            isAudioPlaying = false,
            isVideoPlaying = true,
            onToggleAudio = { },
            onToggleVideo = { },
            batteryLevel = DEVICE_STATE_DATA_UNAVAILABLE,
            signalQuality = 93,
            connectionState = ConnectionState.Connected(makePreviewServer("Baby Monitor-1234")),
            onDisconnected = { },
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
            isAudioPlaying = false,
            isVideoPlaying = true,
            onToggleAudio = { },
            onToggleVideo = { },
            batteryLevel = DEVICE_STATE_DATA_UNAVAILABLE,
            signalQuality = DEVICE_STATE_DATA_UNAVAILABLE,
            connectionState = ConnectionState.Connected(makePreviewServer("Baby Monitor-1234")),
            onDisconnected = { },
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
            isAudioPlaying = false,
            isVideoPlaying = true,
            onToggleAudio = { },
            onToggleVideo = { },
            batteryLevel = DEVICE_STATE_DATA_UNAVAILABLE,
            signalQuality = DEVICE_STATE_DATA_UNAVAILABLE,
            connectionState = ConnectionState.Disconnected(reason = ConnectionState.ErrorReason.ClientQuit),
            onDisconnected = { },
        )
    }
