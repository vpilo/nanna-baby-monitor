package org.vpilo.babymonitor.network.client.websockets

import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.websocket.Frame
import org.koin.mp.KoinPlatform
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.EncodedVideoStreamChunk
import org.vpilo.babymonitor.model.repository.StreamingVideoRepository
import org.vpilo.babymonitor.network.client.StreamingVideoReceiverRepository


internal suspend fun DefaultClientWebSocketSession.videoStreamingClientWebSocket() {
    Logger.w(TAG) { "WebSocket connection established with the server." }

    val repository = KoinPlatform.getKoin().get<StreamingVideoRepository>() as StreamingVideoReceiverRepository

    while (true) {
        when (val frame = incoming.receiveCatching().getOrNull() ?: break) {
            is Frame.Binary -> {
                repository.collector.emit(EncodedVideoStreamChunk(data = frame.data, isCodecConfig = false, isKeyFrame = false))
            }

            is Frame.Text -> {
                Logger.d(TAG) { "Received frame of type ${frame.frameType}: $frame" }
                /*
                 converter?.deserialize(
                     charset = Charset.defaultCharset(),
                     typeInfo = typeInfo<SomeClass>(),
                     content = frame,
                 )
                 */
            }

            else -> {
                Logger.d(TAG) { "Received frame of type ${frame.frameType}" }
                continue
            }
        }
    }
}

private const val TAG = "NetworkClient-Streaming"
