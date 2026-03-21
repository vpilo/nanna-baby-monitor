package org.vpilo.babymonitor.app.client

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.Flow
import org.vpilo.babymonitor.model.AudioFrame
import org.vpilo.babymonitor.presentation.Theme

@Composable
fun ClientHomeScreenRoot(
    modifier: Modifier = Modifier,
    viewModel: ClientHomeViewModel,
) {
    ClientHomeScreen(
        modifier = modifier.fillMaxSize(),
        frames = viewModel.frames,
        chunks = viewModel.audio,
    )
}

@Composable
private fun ClientHomeScreen(
    modifier: Modifier = Modifier,
    frames: Flow<ImageBitmap>,
    chunks: Flow<AudioFrame>,
) {
    Column(modifier = modifier) {
        Text(
            text = "Monitor",
            style = MaterialTheme.typography.titleMedium,
            color = Theme.Colors.text,
        )
        AudioFeed(chunks = chunks)
        CameraFeed(frames = frames)
    }
}
