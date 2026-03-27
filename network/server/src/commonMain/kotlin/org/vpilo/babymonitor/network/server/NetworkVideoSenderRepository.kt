package org.vpilo.babymonitor.network.server

import kotlinx.coroutines.flow.asSharedFlow
import org.vpilo.babymonitor.codec.VideoEncoder
import org.vpilo.babymonitor.model.EncodedAudioStreamChunk
import org.vpilo.babymonitor.model.EncodedVideoStreamChunk
import org.vpilo.babymonitor.model.MediaFormats
import org.vpilo.babymonitor.model.MutableStreamingVideoFlow
import org.vpilo.babymonitor.model.StreamingVideoFlow
import org.vpilo.babymonitor.model.VideoCaptureRepository
import org.vpilo.babymonitor.model.makeMutableStreamingVideoFlow
import org.vpilo.babymonitor.model.repository.SharedResourceHolder
import org.vpilo.babymonitor.model.repository.StreamingVideoSenderRepository
import kotlin.coroutines.CoroutineContext

internal class NetworkVideoSenderRepository(
    videoRepository: VideoCaptureRepository,
    coroutineContext: CoroutineContext,
) : SharedResourceHolder<EncodedVideoStreamChunk>(
        bufferCapacity = MediaFormats.BufferSizes.MAX_SAMPLE_BUFFER_SIZE,
    ),
    StreamingVideoSenderRepository {
    override val chunks: StreamingVideoFlow = collector.asSharedFlow()

    private val encoder: VideoEncoder =
        VideoEncoder(
            input = videoRepository.frames,
            output = collector,
            coroutineContext = coroutineContext,
        )

    override fun start() {
        encoder.start()
    }

    override fun stop() {
        encoder.stop()
    }
}
