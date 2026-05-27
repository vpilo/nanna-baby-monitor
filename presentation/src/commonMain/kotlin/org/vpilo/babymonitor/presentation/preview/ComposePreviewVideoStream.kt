package org.vpilo.babymonitor.presentation.preview

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.vpilo.babymonitor.model.VideoStream

/**
 * Video stream type used exclusively to create Composable Previews.
 */
class ComposePreviewVideoStream(
    width: Int,
    height: Int,
    rotation: Int,
    preview: ImageBitmap,
) : VideoStream<ImageBitmap>() {
    override val surface: Flow<ImageBitmap> = flowOf(preview)
    override val isActive: Flow<Boolean> = flowOf(true)

    init {
        setFrameSize(width, height)
        setRotation(rotation)
    }
}
