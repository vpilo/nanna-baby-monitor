package org.vpilo.babymonitor.network.server

import kotlinx.coroutines.flow.asSharedFlow
import org.vpilo.babymonitor.codec.AudioEncoder
import org.vpilo.babymonitor.model.AudioCaptureRepository
import org.vpilo.babymonitor.model.EncodedAudioStreamChunk
import org.vpilo.babymonitor.model.MediaFormats
import org.vpilo.babymonitor.model.MutableStreamingAudioFlow
import org.vpilo.babymonitor.model.StreamingAudioFlow
import org.vpilo.babymonitor.model.makeMutableStreamingAudioFlow
import org.vpilo.babymonitor.model.repository.SharedResourceHolder
import org.vpilo.babymonitor.model.repository.StreamingAudioSenderRepository
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KClass

internal class NetworkAudioSenderRepository(
    audioRepository: AudioCaptureRepository,
    coroutineContext: CoroutineContext,
) : SharedResourceHolder<EncodedAudioStreamChunk>(
        bufferCapacity = MediaFormats.BufferSizes.MAX_SAMPLE_BUFFER_SIZE,
    ),
    StreamingAudioSenderRepository {
    override val chunks: StreamingAudioFlow = collector.asSharedFlow()

    private val encoder: AudioEncoder =
        AudioEncoder(
            input = audioRepository.samples,
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
