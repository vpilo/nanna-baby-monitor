package org.vpilo.babymonitor.camera.presentation.composables

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
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

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .clipToBounds()
                .rotate(rotation.toFloat()),
    ) {
        Image(
            modifier =
                modifier
                    .sizeIn(
                        maxWidth = originalFrameSize.width.dp,
                        maxHeight = originalFrameSize.height.dp,
                    ).offset(x = panOffset.x.dp, y = panOffset.y.dp),
            bitmap = frame,
            contentScale = ContentScale.Crop,
            contentDescription = null,
        )
    }
}
