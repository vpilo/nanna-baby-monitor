package org.vpilo.babymonitor.app.client

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.vpilo.babymonitor.common.Logger

private const val TAG = "ClientHomeScreen"

@Composable
fun ClientHomeScreen(
    modifier: Modifier = Modifier,
    viewModel: ClientHomeScreenViewModel,
    onDisconnected: () -> Unit,
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effectsFlow.collect {
            when (it) {
                ClientHomeScreenEffect.DisconnectFromServer -> {
                    Logger.d(TAG) { "Disconnecting!" }
                    onDisconnected()
                }
            }
        }
    }

    ClientHomeScreenContent(
        modifier = modifier.fillMaxSize(),
        frames = viewModel.frames,
        isAudioPlaying = state.isAudioPlaying,
        onToggleAudio = { viewModel.send(ClientHomeScreenAction.ToggleAudio) },
    )
}

@Composable
private fun ClientHomeScreenContent(
    modifier: Modifier = Modifier,
    frames: Flow<ImageBitmap>,
    isAudioPlaying: Boolean,
    onToggleAudio: () -> Unit,
) {
    Column(modifier = modifier) {
        AudioFeed(
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
        isAudioPlaying = false,
        onToggleAudio = { },
    )
}
