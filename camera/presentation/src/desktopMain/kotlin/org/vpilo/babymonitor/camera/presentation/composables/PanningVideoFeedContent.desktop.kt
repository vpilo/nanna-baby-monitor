package org.vpilo.babymonitor.camera.presentation.composables

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import org.vpilo.babymonitor.model.DesktopVideoStream
import org.vpilo.babymonitor.model.OpaqueVideoStream

@Composable
internal actual fun PanningVideoFeedContent(
    modifier: Modifier,
    videoStream: OpaqueVideoStream,
    originalFrameSize: IntSize,
    containerSize: IntSize,
    maxPanningAllowed: Offset,
    cropScale: Float,
    panOffset: Offset,
    rotation: Int,
) {
    val desktopVideoStream = checkNotNull(videoStream as? DesktopVideoStream) { "Invalid VideoStream" }
    val frame by desktopVideoStream.surface.collectAsState(initial = ImageBitmap(1, 1))

    PannableVideoFrame(
        modifier = modifier.fillMaxSize(),
        frame = frame,
        originalFrameSize = originalFrameSize,
        cropScale = cropScale,
        panOffset = panOffset,
        rotation = rotation,
    )
}
