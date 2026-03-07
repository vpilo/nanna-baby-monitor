package org.vpilo.babymonitor.network.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.vpilo.babymonitor.network.client.websockets.webSocketClientStreaming
import org.vpilo.babymonitor.network.common.Constants
import org.vpilo.babymonitor.network.common.Endpoints

private val networkClient: HttpClient by lazy {
    HttpClient(CIO) {
        install(WebSockets) {
            contentConverter = KotlinxWebsocketSerializationConverter(Json)
        }
    }
}

suspend fun createNetworkClient(coroutineDispatcher: CoroutineDispatcher = Dispatchers.IO) {
    withContext(coroutineDispatcher) {
        networkClient.webSocket(
            method = HttpMethod.Get,
            host = Constants.CLIENT_ADDRESS,
            port = Constants.COMMUNICATION_PORT,
            path = Endpoints.STREAM,
            block = { webSocketClientStreaming() },
        )
    }
}
