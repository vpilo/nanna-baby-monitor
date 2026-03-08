package org.vpilo.babymonitor.network.client.websockets

import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.converter
import io.ktor.util.reflect.typeInfo
import io.ktor.websocket.Frame
import org.koin.mp.KoinPlatform
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.oldPreviewFeedData
import org.vpilo.babymonitor.model.CameraFrameProperties
import org.vpilo.babymonitor.model.VideoFeedRepository
import org.vpilo.babymonitor.network.client.VideoFeedReceiverRepository
import java.nio.charset.Charset
import kotlin.time.Clock


internal suspend fun DefaultClientWebSocketSession.webSocketClientStreaming() {
    val repository = KoinPlatform.getKoin().getOrNull<VideoFeedRepository>()
    check(repository is VideoFeedReceiverRepository) { "Dependency injection error - wrong repository" }

    Logger.w(TAG) { "WebSocket connection established with the server." }

    var cameraFrameProperties: CameraFrameProperties? = null
    while (true) {
        when (val frame = incoming.receiveCatching().getOrNull() ?: break) {
            is Frame.Binary -> {
                val frameData = frame.data
                if (cameraFrameProperties == null) {
                    Logger.w(TAG) { "Received frame data before frame properties. Skipping." }
                    continue
                }
//                repository.chunks.emit(
//                    oldPreviewFeedData(
//                        data = frameData,
//                        width = cameraFrameProperties.width,
//                        height = cameraFrameProperties.height,
//                        rotation = cameraFrameProperties.rotation,
//                        timestamp = Clock.System.now(),
//                    ),
//                )
            }

            is Frame.Text -> {
                converter?.deserialize(
                    charset = Charset.defaultCharset(),
                    typeInfo = typeInfo<CameraFrameProperties>(),
                    content = frame,
                )
                    ?.let {
                        cameraFrameProperties = it as CameraFrameProperties?
                    }
                    ?: Logger.w(TAG) { "Failed to deserialize frame properties. Skipping." }
            }

            else -> {
                Logger.d(TAG) { "Received frame of type ${frame.frameType}" }
                continue
            }
        }
    }
}

private const val TAG = "NetworkClient-Streaming"
