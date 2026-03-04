package org.vpilo.babymonitor.network.server

import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.sendSerialized
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.flow.onCompletion
import kotlinx.serialization.json.Json
import org.koin.mp.KoinPlatform
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.CameraFrameProperties
import org.vpilo.babymonitor.model.VideoFeedRepository
import java.util.Collections
import kotlin.time.Duration.Companion.seconds

fun Application.configureSockets() {
    install(WebSockets) {
        contentConverter = KotlinxWebsocketSerializationConverter(Json)
        pingPeriod = 15.seconds
        timeout = 15.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
    routing {
        val sessions =
            Collections.synchronizedList<WebSocketServerSession>(ArrayList())

        // FIXME If a connection is made before a role is chosen, this will fail.
        webSocket("/stream") {
            sessions.add(this)
            val repository = KoinPlatform.getKoin().getOrNull<VideoFeedRepository>()
            if (repository == null) {
                Logger.w(TAG) { "VideoFeedRepository not available." }
                return@webSocket
            }

            repository.start()

            var lastFrameProperties: CameraFrameProperties? = null

            repository.frames
                .onCompletion {
                    repository.stop()
                    close(CloseReason(CloseReason.Codes.GOING_AWAY, "Camera feed ended."))
                }
                .collect {
                    val frameProperties = CameraFrameProperties(it.width, it.height, it.rotation)
                    if (frameProperties != lastFrameProperties) {
                        lastFrameProperties = frameProperties
                        sendSerialized(lastFrameProperties)
                    }

                    send(Frame.Binary(fin = true, data = it.data))
                }

        }

    }
}

private const val TAG = "ServerSockets"
