package org.vpilo.babymonitor.network.client

import kotlinx.coroutines.flow.asSharedFlow
import org.vpilo.babymonitor.codec.AudioDecoder
import org.vpilo.babymonitor.model.AudioFrame
import org.vpilo.babymonitor.model.AudioFrameFlow
import org.vpilo.babymonitor.model.MediaFormats
import org.vpilo.babymonitor.model.repository.SharedResourceHolder
import org.vpilo.babymonitor.model.repository.StreamingAudioReceiverRepository
import kotlin.coroutines.CoroutineContext

internal class NetworkAudioReceiverRepository(
    dataSource: NetworkAudioDataSource,
    coroutineContext: CoroutineContext,
) : StreamingAudioReceiverRepository, SharedResourceHolder<AudioFrame>(
    bufferCapacity = MediaFormats.BufferSizes.MAX_SAMPLE_BUFFER_SIZE,
) {

    override val chunks: AudioFrameFlow = collector.asSharedFlow()

    private val decoder: AudioDecoder = AudioDecoder(
        input = dataSource.audioFrames,
        output = collector,
        coroutineContext = coroutineContext,
    )

    override fun start() {
        decoder.start()
    }

    override fun stop() {
        decoder.stop()
    }

    override val TAG = NetworkAudioReceiverRepository::class
}
