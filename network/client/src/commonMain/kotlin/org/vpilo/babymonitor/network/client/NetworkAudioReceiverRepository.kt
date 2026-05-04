package org.vpilo.babymonitor.network.client

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.codec.AudioDecoder
import org.vpilo.babymonitor.model.AudioFrame
import org.vpilo.babymonitor.model.AudioFrameFlow
import org.vpilo.babymonitor.model.MediaFormats
import org.vpilo.babymonitor.model.repository.SharedResourceHolder
import org.vpilo.babymonitor.model.repository.StreamingAudioReceiverRepository
import org.vpilo.babymonitor.network.client.websockets.audioStreamingClientWebSocket
import org.vpilo.babymonitor.network.common.Endpoints
import kotlin.coroutines.CoroutineContext

internal class NetworkAudioReceiverRepository(
    dataSource: NetworkAudioDataSource,
    private val serverSelectionDataSource: ServerSelectionDataSource,
    coroutineContext: CoroutineContext,
) : SharedResourceHolder<AudioFrame>(
        bufferCapacity = MediaFormats.BufferSizes.MAX_SAMPLE_BUFFER_SIZE,
    ),
    StreamingAudioReceiverRepository {
    override val chunks: AudioFrameFlow = collector.asSharedFlow()

    private val decoder: AudioDecoder =
        AudioDecoder(
            input = dataSource.audioFrames,
            output = collector,
            coroutineContext = coroutineContext,
        )

    private var handler: WebSocketConnectionHandler? = null
    private var connectionJob: Job? = null

    override fun start() {
        connectionJob =
            coroutineScope.launch {
                try {
                    serverSelectionDataSource.server.collect { target ->
                        handler?.disconnect()
                        handler = null
                        if (target == null) return@collect
                        handler =
                            WebSocketConnectionHandler(
                                server = target,
                                endpointPath = Endpoints.STREAM_AUDIO,
                                sessionBlock = { audioStreamingClientWebSocket() },
                                coroutineScope = coroutineScope,
                                reconnect = true,
                            ).apply { connect() }
                    }
                } finally {
                    handler?.disconnect()
                    handler = null
                }
            }
        decoder.start()
    }

    override fun stop() {
        connectionJob?.cancel()
        connectionJob = null
        decoder.stop()
    }
}
