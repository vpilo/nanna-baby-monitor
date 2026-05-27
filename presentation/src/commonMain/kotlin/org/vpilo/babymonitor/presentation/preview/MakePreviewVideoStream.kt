package org.vpilo.babymonitor.presentation.preview

import androidx.compose.ui.graphics.ImageBitmap
import org.vpilo.babymonitor.model.VideoStream

fun makePreviewVideoStream(
    width: Int = 1280,
    height: Int = 720,
    rotation: Int = 0,
    isDarkMode: Boolean = false,
): VideoStream<ImageBitmap> =
    ComposePreviewVideoStream(
        width,
        height,
        rotation,
        makePlaceholderCameraFrame(width, height, rotation, isDarkMode),
    )
