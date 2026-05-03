package org.vpilo.babymonitor.network.client

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.codec.AudioDecoder
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.AudioFrame
import org.vpilo.babymonitor.model.AudioFrameFlow
import org.vpilo.babymonitor.model.MediaFormats
import org.vpilo.babymonitor.model.repository.SharedResourceHolder
import org.vpilo.babymonitor.model.repository.StreamingAudioReceiverRepository
import org.vpilo.babymonitor.network.client.websockets.audioStreamingClientWebSocket
import org.vpilo.babymonitor.network.common.Constants
import org.vpilo.babymonitor.network.common.Endpoints
import kotlin.coroutines.CoroutineContext

internal class NetworkAudioReceiverRepository(
    dataSource: NetworkAudioDataSource,
    private val connectionTargetDataSource: ConnectionTargetDataSource,
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
    private var targetJob: Job? = null

    override fun start() {
        decoder.start()
        targetJob =
            coroutineScope.launch {
                try {
                    connectionTargetDataSource.target.collect { target ->
                        handler?.disconnect()
                        handler = null
                        if (target == null) return@collect
                        handler =
                            WebSocketConnectionHandler(
                                hosts = setOf(target.address),
                                endpointPath = Endpoints.STREAM_AUDIO,
                                serverId = target.serverId,
                                sessionBlock = { _ -> audioStreamingClientWebSocket() },
                                onDisconnected = {
                                    Logger.i(TAG) { "Audio disconnected, reconnecting" }
                                    delay(Constants.RECONNECTION_TIMEOUT)
                                    handler?.connect()
                                },
                                coroutineScope = coroutineScope,
                            ).apply { connect() }
                    }
                } finally {
                    handler?.disconnect()
                    handler = null
                }
            }
    }

    override fun stop() {
        targetJob?.cancel()
        targetJob = null
        decoder.stop()
    }
}
