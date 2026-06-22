package org.vpilo.babymonitor.network.client

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.codec.VideoDecoder
import org.vpilo.babymonitor.model.OpaqueVideoStream
import org.vpilo.babymonitor.model.repository.StreamingVideoReceiverRepository
import org.vpilo.babymonitor.network.client.websockets.videoStreamingClientWebSocket
import org.vpilo.babymonitor.network.common.Constants
import org.vpilo.babymonitor.network.common.Endpoints
import kotlin.coroutines.CoroutineContext

internal class NetworkVideoReceiverRepository(
    dataSource: NetworkVideoDataSource,
    private val serverSelectionDataSource: ServerSelectionDataSource,
    coroutineContext: CoroutineContext,
) : StreamingVideoReceiverRepository {
    private val coroutineScope = CoroutineScope(coroutineContext)

    private val decoder: VideoDecoder =
        VideoDecoder(
            input = dataSource.frames,
            coroutineContext = coroutineContext,
        )

    override val videoStream: OpaqueVideoStream = decoder.videoStream
    override val isActive: Flow<Boolean> = videoStream.isActive

    private var handler: WebSocketConnectionHandler? = null
    private var connectionJob: Job? = null

    init {
        isActive
            .map { isActive ->
                if (isActive) {
                    start()
                } else {
                    stop()
                }
            }.launchIn(coroutineScope)
    }

    private fun start() {
        connectionJob =
            coroutineScope.launch {
                try {
                    serverSelectionDataSource.server.collect { target ->
                        handler?.disconnect()
                        handler = null
                        if (target == null) return@collect
                        handler =
                            WebSocketConnectionHandler(
                                device = target,
                                endpointPath = Endpoints.STREAM_VIDEO,
                                sessionBlock = { videoStreamingClientWebSocket() },
                                coroutineScope = coroutineScope,
                                onDisconnected = {
                                    delay(Constants.RECONNECTION_TIMEOUT)
                                    handler?.connect()
                                },
                            ).apply { connect() }
                    }
                } finally {
                    handler?.disconnect()
                    handler = null
                }
            }
        decoder.start()
    }

    private fun stop() {
        decoder.stop()
        connectionJob?.cancel()
        connectionJob = null
    }
}
