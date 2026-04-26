package org.vpilo.babymonitor.app.client.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import babymonitor.appcommon.generated.resources.Res
import babymonitor.appcommon.generated.resources.app_title_client_home
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.model.CaptureMode
import org.vpilo.babymonitor.model.repository.DEVICE_STATE_DATA_UNAVAILABLE
import org.vpilo.babymonitor.presentation.AppPreviewTheme
import org.vpilo.babymonitor.presentation.Theme
import org.vpilo.babymonitor.presentation.client.BatteryState
import org.vpilo.babymonitor.presentation.client.SignalState
import org.vpilo.babymonitor.presentation.composables.AppDestination
import org.vpilo.babymonitor.presentation.composables.AppDestinationMainAction
import org.vpilo.babymonitor.presentation.composables.Backdrop
import org.vpilo.babymonitor.presentation.preview.makePlaceholderCameraFrame

@Composable
fun ClientHomeScreen(
    modifier: Modifier = Modifier,
    viewModel: ClientHomeScreenViewModel,
    onDisconnected: () -> Unit,
    onMenuClicked: () -> Unit,
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LifecycleResumeEffect(Unit) {
        val job =
            scope.launch {
                viewModel.effectsFlow.collect {
                    when (it) {
                        ClientHomeScreenEffect.DisconnectedFromServer -> onDisconnected()
                    }
                }
            }
        onPauseOrDispose { job.cancel() }
    }

    AppDestination(
        title = Res.string.app_title_client_home,
        mainAction = AppDestinationMainAction.Menu,
        onMainActionClicked = {
            viewModel.disconnect()
            onMenuClicked()
        },
    ) {
        ClientHomeScreenContent(
            modifier = modifier.fillMaxSize(),
            frames = viewModel.frames,
            captureMode = state.captureMode,
            isAudioPlaying = state.isAudioPlaying,
            isVideoPlaying = state.isVideoPlaying,
            onToggleAudio = { viewModel.send(ClientHomeScreenAction.ToggleAudio) },
            onToggleVideo = { viewModel.send(ClientHomeScreenAction.ToggleVideo) },
            batteryLevel = state.batteryLevel,
            signalQuality = state.signalQuality,
        )
    }
}

@Composable
private fun ClientHomeScreenContent(
    modifier: Modifier = Modifier,
    frames: Flow<ImageBitmap>,
    captureMode: CaptureMode,
    isAudioPlaying: Boolean,
    isVideoPlaying: Boolean,
    onToggleAudio: () -> Unit,
    onToggleVideo: () -> Unit,
    batteryLevel: Int,
    signalQuality: Int,
) {
    Box(modifier = modifier) {
        CameraFeed(frames = frames)
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
    }
}

@Preview
@Composable
private fun ClientHomeScreenPreview() =
    AppPreviewTheme {
        ClientHomeScreenContent(
            modifier = Modifier.fillMaxSize(),
            frames = flowOf(makePlaceholderCameraFrame()),
            captureMode = CaptureMode.AUDIO_AND_VIDEO,
            isAudioPlaying = false,
            isVideoPlaying = true,
            onToggleAudio = { },
            onToggleVideo = { },
            batteryLevel = 5,
            signalQuality = 3,
        )
    }

@Preview
@Composable
private fun ClientHomeScreenVideoOnlyPreview() =
    AppPreviewTheme {
        ClientHomeScreenContent(
            modifier = Modifier.fillMaxSize(),
            frames = flowOf(makePlaceholderCameraFrame()),
            captureMode = CaptureMode.VIDEO_ONLY,
            isAudioPlaying = false,
            isVideoPlaying = true,
            onToggleAudio = { },
            onToggleVideo = { },
            batteryLevel = DEVICE_STATE_DATA_UNAVAILABLE,
            signalQuality = 93,
        )
    }

@Preview
@Composable
private fun ClientHomeScreenNoSignalOrBatteryPreview() =
    AppPreviewTheme {
        ClientHomeScreenContent(
            modifier = Modifier.fillMaxSize(),
            frames = flowOf(makePlaceholderCameraFrame()),
            captureMode = CaptureMode.AUDIO_AND_VIDEO,
            isAudioPlaying = false,
            isVideoPlaying = true,
            onToggleAudio = { },
            onToggleVideo = { },
            batteryLevel = DEVICE_STATE_DATA_UNAVAILABLE,
            signalQuality = DEVICE_STATE_DATA_UNAVAILABLE,
        )
    }
