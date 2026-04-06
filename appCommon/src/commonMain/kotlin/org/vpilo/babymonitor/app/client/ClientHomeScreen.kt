package org.vpilo.babymonitor.app.client

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import babymonitor.appcommon.generated.resources.Res
import babymonitor.appcommon.generated.resources.app_title_client_home
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.CaptureMode
import org.vpilo.babymonitor.model.repository.DEVICE_STATE_DATA_UNAVAILABLE
import org.vpilo.babymonitor.presentation.client.BatteryState
import org.vpilo.babymonitor.presentation.client.SignalState
import org.vpilo.babymonitor.presentation.composables.AppDestination
import org.vpilo.babymonitor.presentation.preview.placeholderFrame

private const val TAG = "ClientHomeScreen"

@Composable
fun ClientHomeScreen(
    modifier: Modifier = Modifier,
    viewModel: ClientHomeScreenViewModel,
    onDisconnected: () -> Unit,
    onBackClicked: () -> Unit,
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LifecycleResumeEffect(Unit) {
        val job =
            scope.launch {
                viewModel.effectsFlow.collect {
                    when (it) {
                        ClientHomeScreenEffect.DisconnectedFromServer -> {
                            Logger.d(TAG) { "Was disconnected!" }
                            onDisconnected()
                        }
                    }
                }
            }
        onPauseOrDispose { job.cancel() }
    }

    AppDestination(
        title = Res.string.app_title_client_home,
        onBackClicked = {
            viewModel.disconnect()
            onBackClicked()
        },
    ) {
        ClientHomeScreenContent(
            modifier = modifier.fillMaxSize(),
            frames = viewModel.frames,
            captureMode = state.captureMode,
            isAudioPlaying = state.isAudioPlaying,
            onToggleAudio = { viewModel.send(ClientHomeScreenAction.ToggleAudio) },
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
    onToggleAudio: () -> Unit,
    batteryLevel: Int,
    signalQuality: Int,
) {
    Box(modifier = modifier) {
        CameraFeed(
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.FillWidth,
            frames = frames,
        )
        AudioFeed(
            canPlay = captureMode != CaptureMode.VIDEO_ONLY,
            isPlaying = isAudioPlaying,
            onToggle = onToggleAudio,
        )
        Row(
            modifier = Modifier.align(androidx.compose.ui.Alignment.TopEnd),
        ) {
            BatteryState(batteryLevel = batteryLevel)
            SignalState(signalQuality = signalQuality)
        }
    }
}

@Preview
@Composable
private fun ClientHomeScreenPreview() {
    ClientHomeScreenContent(
        modifier = Modifier.fillMaxSize(),
        frames = flowOf(placeholderFrame),
        captureMode = CaptureMode.AUDIO_AND_VIDEO,
        isAudioPlaying = false,
        onToggleAudio = { },
        batteryLevel = 5,
        signalQuality = 3,
    )
}

@Preview
@Composable
private fun ClientHomeScreenVideoOnlyPreview() {
    ClientHomeScreenContent(
        modifier = Modifier.fillMaxSize(),
        frames = flowOf(placeholderFrame),
        captureMode = CaptureMode.VIDEO_ONLY,
        isAudioPlaying = false,
        onToggleAudio = { },
        batteryLevel = 95,
        signalQuality = 93,
    )
}

@Preview
@Composable
private fun ClientHomeScreenNoSignalOrBatteryPreview() {
    ClientHomeScreenContent(
        modifier = Modifier.fillMaxSize(),
        frames = flowOf(placeholderFrame),
        captureMode = CaptureMode.AUDIO_AND_VIDEO,
        isAudioPlaying = false,
        onToggleAudio = { },
        batteryLevel = DEVICE_STATE_DATA_UNAVAILABLE,
        signalQuality = DEVICE_STATE_DATA_UNAVAILABLE,
    )
}
