package org.vpilo.babymonitor.app.client.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.LifecycleStartEffect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.presentation.AppPreviewTheme
import org.vpilo.babymonitor.presentation.composables.FpsCounter
import org.vpilo.babymonitor.presentation.composables.PannableImage
import org.vpilo.babymonitor.presentation.preview.makePlaceholderCameraFrame

private const val TAG = "CameraFeed"

@Composable
fun CameraFeed(
    modifier: Modifier = Modifier,
    frames: Flow<ImageBitmap>,
) {
    var frame by remember { mutableStateOf<ImageBitmap?>(null) }
    val scope = rememberCoroutineScope()

    LifecycleStartEffect(Unit) {
        Logger.d(TAG) { "Started showing feed" }
        val frameJob =
            scope.launch {
                frames.collect { frame = it }
            }

        onStopOrDispose {
            Logger.d(TAG) { "Stopped showing feed" }
            frameJob.cancel()
            frame = null
        }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
        frame?.let { frame ->
            PannableImage(
                bitmap = frame,
                modifier = modifier.fillMaxSize(),
            )
            FpsCounter(frameKey = frame)
        }
    }
}

@Preview
@Composable
private fun CameraFeedPreview() =
    AppPreviewTheme {
        CameraFeed(
            modifier = Modifier,
            frames = flowOf(makePlaceholderCameraFrame()),
        )
    }
