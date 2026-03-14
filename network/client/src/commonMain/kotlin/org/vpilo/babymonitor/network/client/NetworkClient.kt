package org.vpilo.babymonitor.network.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.ClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.cbor.cbor
import io.ktor.websocket.CloseReason
import io.ktor.websocket.close
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.network.client.websockets.videoStreamingClientWebSocket
import org.vpilo.babymonitor.network.common.Constants
import org.vpilo.babymonitor.network.common.Endpoints
import java.net.ConnectException

private val networkClient: HttpClient by lazy {
    HttpClient(CIO) {
        install(ContentNegotiation) {
            @OptIn(ExperimentalSerializationApi::class)
            cbor(
                Cbor {
                    ignoreUnknownKeys = false
                },
            )
        }
        install(WebSockets)
    }
}

private var currentSession: ClientWebSocketSession? = null

suspend fun createNetworkClient(coroutineDispatcher: CoroutineDispatcher = Dispatchers.IO) {
    coroutineScope {
//        launch(coroutineDispatcher) {
//            try {
//                networkClient.webSocket(
//                    method = HttpMethod.Get,
//                    host = Constants.CLIENT_ADDRESS,
//                    port = Constants.COMMUNICATION_PORT,
//                    path = Endpoints.STREAM_AUDIO,
//                ) {
//                    if (currentSession != null) {
//                        currentSession?.close(CloseReason(CloseReason.Codes.GOING_AWAY, "New audio session created."))
//                    }
//                    currentSession = this
//                    audioStreamingClientWebSocket()
//                }
//            } catch (ex: ConnectException) {
//                Logger.w("Client") { "Connection to server failed: ${ex.message}" }
//            }
//        }
        launch(coroutineDispatcher) {
            try {
                networkClient.webSocket(
                    method = HttpMethod.Get,
                    host = Constants.CLIENT_ADDRESS,
                    port = Constants.COMMUNICATION_PORT,
                    path = Endpoints.STREAM_VIDEO,
                ) {
                    if (currentSession != null) {
                        currentSession?.close(CloseReason(CloseReason.Codes.GOING_AWAY, "New video session created."))
                    }
                    currentSession = this
                    videoStreamingClientWebSocket()
                }
            } catch (ex: ConnectException) {
                Logger.w("Client") { "Connection to server failed: ${ex.message}" }
            }
        }
    }
}
