package org.vpilo.babymonitor.network.server

import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.vpilo.babymonitor.network.common.Constants
import org.vpilo.babymonitor.network.common.Endpoints
import org.vpilo.babymonitor.network.server.websockets.webSocketServerStreaming
import kotlin.time.Duration.Companion.seconds

suspend fun createNetworkServer(coroutineDispatcher: CoroutineDispatcher = Dispatchers.IO) {
    withContext(coroutineDispatcher) {
        embeddedServer(CIO, port = Constants.COMMUNICATION_PORT, host = Constants.SERVER_LISTEN_ADDRESS, module = Application::module)
            .start(wait = true)
    }
}

fun Application.module() {
    install(WebSockets) {
        contentConverter = KotlinxWebsocketSerializationConverter(Json)
        pingPeriod = 15.seconds
        timeout = 15.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    routing {
        webSocket(Endpoints.STREAM) { webSocketServerStreaming() }
    }
}
