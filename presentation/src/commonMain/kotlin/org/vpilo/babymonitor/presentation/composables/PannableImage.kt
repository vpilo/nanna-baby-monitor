package org.vpilo.babymonitor.presentation.composables

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import org.vpilo.babymonitor.presentation.AppPreviewTheme
import org.vpilo.babymonitor.presentation.preview.makePlaceholderCameraFrame
import kotlin.math.max

@Composable
fun PannableImage(
    bitmap: ImageBitmap,
    modifier: Modifier = Modifier,
) {
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val latestBitmap by rememberUpdatedState(bitmap)

    val limit = panLimit(bitmap, containerSize)
    val biasX = if (limit.x > 0f) (panOffset.x / limit.x).coerceIn(-1f, 1f) else 0f
    val biasY = if (limit.y > 0f) (panOffset.y / limit.y).coerceIn(-1f, 1f) else 0f

    Image(
        bitmap = bitmap,
        contentScale = ContentScale.Crop,
        alignment = BiasAlignment(biasX, biasY),
        contentDescription = null,
        modifier =
            modifier
                .onSizeChanged { containerSize = it }
                .pointerInput(Unit) {
                    detectDragGestures { _, dragAmount ->
                        val currentLimit = panLimit(latestBitmap, containerSize)
                        if (currentLimit == Offset.Zero) return@detectDragGestures
                        panOffset =
                            Offset(
                                (panOffset.x - dragAmount.x).coerceIn(-currentLimit.x, currentLimit.x),
                                (panOffset.y - dragAmount.y).coerceIn(-currentLimit.y, currentLimit.y),
                            )
                    }
                },
    )
}

/**
 * Maximum absolute pan offset (per axis) for a [bitmap] cropped to fill [containerSize].
 * Returns [Offset.Zero] when the container hasn't been measured or the image fits without excess.
 */
private fun panLimit(
    bitmap: ImageBitmap,
    containerSize: IntSize,
): Offset {
    if (containerSize == IntSize.Zero) return Offset.Zero
    val cropScale =
        max(
            containerSize.width.toFloat() / bitmap.width,
            containerSize.height.toFloat() / bitmap.height,
        )
    return Offset(
        x = (bitmap.width * cropScale - containerSize.width).coerceAtLeast(0f) / 2f,
        y = (bitmap.height * cropScale - containerSize.height).coerceAtLeast(0f) / 2f,
    )
}

@Preview
@Composable
private fun PannableImagePreview() =
    AppPreviewTheme {
        PannableImage(
            bitmap = makePlaceholderCameraFrame(),
        )
    }
