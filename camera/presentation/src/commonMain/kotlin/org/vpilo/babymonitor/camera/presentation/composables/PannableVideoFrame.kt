package org.vpilo.babymonitor.camera.presentation.composables

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntSize

/**
 * Draws [frame] scaled by [cropScale], rotated by [rotation], and translated by [panOffset],
 * all anchored on the bitmap's center. Inputs are in layout pixels.
 */
@Composable
internal fun PannableVideoFrame(
    modifier: Modifier,
    frame: ImageBitmap,
    originalFrameSize: IntSize,
    cropScale: Float,
    panOffset: Offset,
    rotation: Int,
) {
    Canvas(modifier = modifier) {
        if (originalFrameSize == IntSize.Zero) return@Canvas
        withTransform({
            translate(
                left = size.width / 2f + panOffset.x,
                top = size.height / 2f + panOffset.y,
            )
            rotate(degrees = rotation.toFloat(), pivot = Offset.Zero)
            scale(scaleX = cropScale, scaleY = cropScale, pivot = Offset.Zero)
        }) {
            drawImage(
                image = frame,
                topLeft =
                    Offset(
                        x = -originalFrameSize.width / 2f,
                        y = -originalFrameSize.height / 2f,
                    ),
            )
        }
    }
}
