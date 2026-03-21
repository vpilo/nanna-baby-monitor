package org.vpilo.babymonitor.network.server

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.vpilo.babymonitor.model.repository.NetworkServerRepository
import org.vpilo.babymonitor.network.common.Constants
import org.vpilo.babymonitor.network.common.DiscoveryManager
import org.vpilo.babymonitor.network.common.Endpoints
import org.vpilo.babymonitor.network.server.websockets.audioStreamingServerWebSocket
import org.vpilo.babymonitor.network.server.websockets.videoStreamingServerWebSocket
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds

internal class DefaultNetworkServerRepository(
    private val discoveryManager: DiscoveryManager,
    private val coroutineContext: CoroutineContext,
) : NetworkServerRepository {

    private var server: EmbeddedServer<*, *>? = null


    private val state = MutableStateFlow(false)
    override val stateFlow: Flow<Boolean> = state.asStateFlow()

    override suspend fun start() {
        if (server != null) {
            return
        }

        discoveryManager.registerService()

        withContext(coroutineContext) {
            val newServer =
                embeddedServer(
                    factory = CIO,
                    module = Application::module,
                    host = Constants.SERVICES_LISTEN_ADDRESS.hostAddress,
                    port = Constants.WEBSOCKET_PORT,
                )
            server = newServer
            newServer.start(wait = true)
            state.value = true
        }
    }

    override fun stop() {
        discoveryManager.unregisterService()
        server?.stop(gracePeriodMillis = 1000, timeoutMillis = 5000)
        server = null
        state.value = false
    }
}

private fun Application.module() {
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 15.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    routing {
        webSocket(Endpoints.STREAM_AUDIO) { audioStreamingServerWebSocket() }
        webSocket(Endpoints.STREAM_VIDEO) { videoStreamingServerWebSocket() }
    }
}
