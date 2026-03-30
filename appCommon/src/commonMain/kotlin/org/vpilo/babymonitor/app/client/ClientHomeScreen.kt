package org.vpilo.babymonitor.app.client

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
        val job = scope.launch {
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
) {
    Column(modifier = modifier) {
        AudioFeed(
            canPlay = captureMode != CaptureMode.VIDEO_ONLY,
            isPlaying = isAudioPlaying,
            onToggle = onToggleAudio,
        )
        CameraFeed(
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.FillWidth,
            frames = frames,
        )
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
    )
}
