package org.vpilo.babymonitor.network.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.converter
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.util.reflect.typeInfo
import io.ktor.websocket.Frame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.CameraFrameData
import org.vpilo.babymonitor.model.CameraFrameProperties
import java.nio.charset.Charset

private val networkClient: HttpClient by lazy {
    HttpClient(CIO) {
        install(WebSockets) {
            contentConverter = KotlinxWebsocketSerializationConverter(Json)
        }
    }
}

suspend fun createNetworkClient() {
    coroutineScope {
        launch(Dispatchers.IO) {
            networkClient.webSocket(method = HttpMethod.Get, host = "127.0.0.1", port = COMMUNICATION_PORT, path = "/stream") {
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
                            NetworkDataCollector.frameCollector.emit(
                                CameraFrameData(
                                    data = frameData,
                                    width = cameraFrameProperties.width,
                                    height = cameraFrameProperties.height,
                                    rotation = cameraFrameProperties.rotation,
                                    timestamp = kotlin.time.Clock.System.now(),
                                ),
                            )
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
        }
    }
}

private const val COMMUNICATION_PORT = 47812

private const val TAG = "NetworkClient"
