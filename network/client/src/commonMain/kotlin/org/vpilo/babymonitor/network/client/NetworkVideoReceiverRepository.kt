package org.vpilo.babymonitor.network.client

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.vpilo.babymonitor.codec.VideoDecoder
import org.vpilo.babymonitor.model.MediaFormats
import org.vpilo.babymonitor.model.repository.SharedResourceHolder
import org.vpilo.babymonitor.model.repository.StreamingVideoReceiverRepository
import kotlin.coroutines.CoroutineContext

internal class NetworkVideoReceiverRepository(
    dataSource: NetworkVideoDataSource,
    coroutineContext: CoroutineContext,
) : SharedResourceHolder<ImageBitmap>(
        bufferCapacity = MediaFormats.BufferSizes.MAX_FRAME_BUFFER_SIZE,
    ),
    StreamingVideoReceiverRepository {
    // TODO probably want to change the raw frame format for the camera, for fewer conversions on either end.
    override val decodedFrames: SharedFlow<ImageBitmap> = collector.asSharedFlow()

    private val decoder: VideoDecoder =
        VideoDecoder(
            input = dataSource.frames,
            output = collector,
            coroutineContext = coroutineContext,
        )

    override fun start() {
        decoder.start()
    }

    override fun stop() {
        decoder.stop()
    }
}
