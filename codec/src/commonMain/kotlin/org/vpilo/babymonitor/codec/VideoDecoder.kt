package org.vpilo.babymonitor.codec

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.MutableSharedFlow
import org.vpilo.babymonitor.model.StreamingVideoFlow
import kotlin.coroutines.CoroutineContext

expect class VideoDecoder(
    input: StreamingVideoFlow,
    output: MutableSharedFlow<ImageBitmap>,
    coroutineContext: CoroutineContext,
) {
    fun start()

    fun stop()
}
