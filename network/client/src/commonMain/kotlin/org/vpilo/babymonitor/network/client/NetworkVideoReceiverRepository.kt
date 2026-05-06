package org.vpilo.babymonitor.network.client

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.codec.VideoDecoder
import org.vpilo.babymonitor.model.MediaFormats
import org.vpilo.babymonitor.model.repository.SharedResourceHolder
import org.vpilo.babymonitor.model.repository.StreamingVideoReceiverRepository
import org.vpilo.babymonitor.network.client.websockets.videoStreamingClientWebSocket
import org.vpilo.babymonitor.network.common.Constants
import org.vpilo.babymonitor.network.common.Endpoints
import kotlin.coroutines.CoroutineContext

internal class NetworkVideoReceiverRepository(
    dataSource: NetworkVideoDataSource,
    private val serverSelectionDataSource: ServerSelectionDataSource,
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

    override fun stop() {
        connectionJob?.cancel()
        connectionJob = null
        decoder.stop()
    }
}
