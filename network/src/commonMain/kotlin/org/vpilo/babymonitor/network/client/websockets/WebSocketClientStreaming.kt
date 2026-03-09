package org.vpilo.babymonitor.network.client.websockets

import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.websocket.Frame
import org.koin.mp.KoinPlatform
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.VideoFeedRepository
import org.vpilo.babymonitor.network.client.VideoFeedReceiverRepository


internal suspend fun DefaultClientWebSocketSession.webSocketClientStreaming() {
    val repository = KoinPlatform.getKoin().getOrNull<VideoFeedRepository>()
    check(repository is VideoFeedReceiverRepository) { "Dependency injection error - wrong repository" }

    Logger.w(TAG) { "WebSocket connection established with the server." }

    while (true) {
        when (val frame = incoming.receiveCatching().getOrNull() ?: break) {
            is Frame.Binary -> {
                TODO()
//                repository.chunks.emit(...)
            }

            is Frame.Text -> {
                TODO()
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
