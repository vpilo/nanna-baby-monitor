package org.vpilo.babymonitor.camera.presentation.composables

import androidx.compose.foundation.AndroidEmbeddedExternalSurface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.unit.IntSize
import org.vpilo.babymonitor.model.AndroidVideoStream
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
    val transform =
        remember(containerSize, originalFrameSize, cropScale, panOffset, rotation) {
            if (containerSize == IntSize.Zero || originalFrameSize == IntSize.Zero) {
                null
            } else {
                // TextureView stretches the buffer anisotropically to fill the view. The trailing
                // scale undoes that stretch (back to buffer pixels); the rest is the same
                // center-anchored crop-fill + rotation + pan the desktop path applies, so the result
                // is a true-aspect, upright, crop-filled image.
                Matrix().apply {
                    translate(
                        x = containerSize.width / 2f + panOffset.x,
                        y = containerSize.height / 2f + panOffset.y,
                    )
                    rotateZ(rotation.toFloat())
                    scale(x = cropScale, y = cropScale)
                    translate(x = -originalFrameSize.width / 2f, y = -originalFrameSize.height / 2f)
                    scale(
                        x = originalFrameSize.width.toFloat() / containerSize.width.toFloat(),
                        y = originalFrameSize.height.toFloat() / containerSize.height.toFloat(),
                    )
                }
            }
        }

    val androidVideoStream = checkNotNull(videoStream as? AndroidVideoStream) { "Invalid VideoStream" }

    AndroidEmbeddedExternalSurface(
        modifier = Modifier.fillMaxSize(),
        surfaceSize = originalFrameSize,
        transform = transform,
    ) {
        onSurface { surface, _, _ ->
            androidVideoStream.postSurface(surface)
            surface.onDestroyed {
                androidVideoStream.postSurface(null)
            }
        }
    }

    DisposableEffect(androidVideoStream) {
        onDispose {
            androidVideoStream.postSurface(null)
        }
    }
}
