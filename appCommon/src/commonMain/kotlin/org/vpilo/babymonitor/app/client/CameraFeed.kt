package org.vpilo.babymonitor.app.client

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.LifecycleStartEffect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.presentation.composables.FpsCounter
import org.vpilo.babymonitor.presentation.preview.placeholderFrame

private const val TAG = "CameraFeed"

@Composable
fun CameraFeed(
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.FillWidth,
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

    if (frame != null) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Image(
                bitmap = frame!!,
                contentScale = contentScale,
                contentDescription = null,
                modifier = modifier,
            )
            FpsCounter(frameKey = frame!!)
        }
    }
}

@Preview
@Composable
private fun CameraFeedPreview() {
    CameraFeed(
        modifier = Modifier,
        frames = flowOf(placeholderFrame),
    )
}
