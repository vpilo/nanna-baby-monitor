package org.vpilo.babymonitor.model

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class DesktopVideoStream : VideoStream<ImageBitmap>() {
    private val mutableSurface =
        MutableSharedFlow<ImageBitmap>(
            replay = 0,
            extraBufferCapacity = MediaFormats.BufferSizes.MAX_FRAME_BUFFER_SIZE,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    override val surface: Flow<ImageBitmap> = mutableSurface.asSharedFlow()

    override val isActive: Flow<Boolean> =
        mutableSurface.subscriptionCount
            .map { it > 0 }
            .distinctUntilChanged()

    fun onFrame(bitmap: ImageBitmap) {
        mutableSurface.tryEmit(bitmap)
        signalFrameRendered()
    }
}
