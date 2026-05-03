package org.vpilo.babymonitor.network.client

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.codec.VideoDecoder
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.MediaFormats
import org.vpilo.babymonitor.model.repository.SharedResourceHolder
import org.vpilo.babymonitor.model.repository.StreamingVideoReceiverRepository
import org.vpilo.babymonitor.network.client.websockets.videoStreamingClientWebSocket
import org.vpilo.babymonitor.network.common.Constants
import org.vpilo.babymonitor.network.common.Endpoints
import kotlin.coroutines.CoroutineContext

internal class NetworkVideoReceiverRepository(
    dataSource: NetworkVideoDataSource,
    private val connectionTargetDataSource: ConnectionTargetDataSource,
    coroutineContext: CoroutineContext,
) : SharedResourceHolder<ImageBitmap>(
        bufferCapacity = MediaFormats.BufferSizes.MAX_FRAME_BUFFER_SIZE,
    ),
    StreamingVideoReceiverRepository {
    override val decodedFrames: SharedFlow<ImageBitmap> = collector.asSharedFlow()

    private val decoder: VideoDecoder =
        VideoDecoder(
            input = dataSource.frames,
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
                                endpointPath = Endpoints.STREAM_VIDEO,
                                serverId = target.serverId,
                                sessionBlock = { _ -> videoStreamingClientWebSocket() },
                                onDisconnected = {
                                    Logger.i(TAG) { "Video disconnected, reconnecting" }
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
