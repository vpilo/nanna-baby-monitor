package org.vpilo.babymonitor.network.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.http.HttpMethod
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.network.client.websockets.audioStreamingClientWebSocket
import org.vpilo.babymonitor.network.client.websockets.videoStreamingClientWebSocket
import org.vpilo.babymonitor.network.common.Constants
import org.vpilo.babymonitor.network.common.Endpoints
import java.net.ConnectException

private val networkClient: HttpClient by lazy {
    HttpClient(CIO) {
        install(WebSockets)
    }
}

suspend fun createNetworkClient(
    onDisconnect: suspend (Throwable?) -> Unit,
    coroutineDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    coroutineScope {
        var audioStreamJob: Job? = null
        var videoStreamJob: Job? = null

        suspend fun onConnectionClosed(exception: Throwable, onDisconnect: suspend (Throwable?) -> Unit) {
            audioStreamJob?.cancel()
            audioStreamJob = null
            videoStreamJob?.cancel()
            videoStreamJob = null

            onDisconnect(
                if (exception is ConnectException) {
                    Logger.i(TAG) { "Connection to server closed." }
                    null
                } else {
                    Logger.w(TAG) { "WebSocket failure (${exception::class.simpleName}): ${exception.localizedMessage}" }
                    exception
                },
            )
        }

        audioStreamJob = launch(coroutineDispatcher) {
            try {
                networkClient.webSocket(
                    method = HttpMethod.Get,
                    host = Constants.CLIENT_ADDRESS,
                    port = Constants.COMMUNICATION_PORT,
                    path = Endpoints.STREAM_AUDIO,
                ) {
                    audioStreamingClientWebSocket()
                }
            } catch (ex: Exception) {
                onConnectionClosed(ex, onDisconnect)
            }
        }
        videoStreamJob = launch(coroutineDispatcher) {
            try {
                networkClient.webSocket(
                    method = HttpMethod.Get,
                    host = Constants.CLIENT_ADDRESS,
                    port = Constants.COMMUNICATION_PORT,
                    path = Endpoints.STREAM_VIDEO,
                ) {
                    videoStreamingClientWebSocket()
                }
            } catch (ex: Exception) {
                onConnectionClosed(ex, onDisconnect)
            }
        }
    }
}

private const val TAG = "NetworkClient"
