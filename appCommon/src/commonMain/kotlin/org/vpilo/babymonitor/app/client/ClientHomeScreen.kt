package org.vpilo.babymonitor.app.client

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.presentation.Theme
import org.vpilo.babymonitor.presentation.composables.BackButton

private const val TAG = "ClientHomeScreen"

@Composable
fun ClientHomeScreen(
    modifier: Modifier = Modifier,
    viewModel: ClientHomeViewModel,
    onDisconnected: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.disconnectedEvents.collect {
            Logger.d(TAG) { "Disconnecting!" }
            onDisconnected()
        }
    }

    ClientHomeScreenContent(
        modifier = modifier.fillMaxSize(),
        frames = viewModel.frames,
        isAudioPlaying = state.isAudioPlaying,
        onToggleAudio = { viewModel.onAction(ClientHomeAction.ToggleAudio) },
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
        CameraFeed(frames = frames)
    }
}
