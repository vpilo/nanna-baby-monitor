package org.vpilo.babymonitor.app.client

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.Flow
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.AudioFrame
import org.vpilo.babymonitor.presentation.Theme
import org.vpilo.babymonitor.presentation.composables.BackButton

private const val TAG = "ClientHomeScreen"

@Composable
fun ClientHomeScreen(
    modifier: Modifier = Modifier,
    viewModel: ClientHomeViewModel,
    onDisconnected: () -> Unit,
    onBackClicked: () -> Unit,
) {
    LaunchedEffect(Unit) {
        viewModel.disconnectedEvents.collect {
            Logger.d(TAG) { "Disconnecting!" }
            onDisconnected()
        }
    }

    ClientHomeScreenContent(
        modifier = modifier.fillMaxSize(),
        onBackClicked = onBackClicked,
        frames = viewModel.frames,
        chunks = viewModel.audio,
    )
}

@Composable
private fun ClientHomeScreenContent(
    modifier: Modifier = Modifier,
    onBackClicked: () -> Unit,
    frames: Flow<ImageBitmap>,
    chunks: Flow<AudioFrame>,
) {
    Column(modifier = modifier) {
        BackButton(
            modifier = Modifier,
            onBackClicked = onBackClicked,
        )

        Text(
            text = "Monitor",
            style = MaterialTheme.typography.titleMedium,
            color = Theme.Colors.text,
        )
        AudioFeed(chunks = chunks)
        CameraFeed(frames = frames)
    }
}
