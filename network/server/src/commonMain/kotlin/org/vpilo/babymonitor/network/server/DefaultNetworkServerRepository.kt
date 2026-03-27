package org.vpilo.babymonitor.network.server

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.ServerReady
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
import org.vpilo.babymonitor.common.Logger
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
            embeddedServer(
                factory = CIO,
                module = Application::module,
                host = Constants.SERVICES_LISTEN_ADDRESS,
                port = Constants.WEBSOCKET_PORT,
            ).apply {
                server = this

                monitor.subscribe(ServerReady) {
                    Logger.i(TAG) { "Server is ready at ${Constants.SERVICES_LISTEN_ADDRESS}" }
                    state.value = true
                }
                monitor.subscribe(ApplicationStopped) {
                    Logger.i(TAG) { "Server is stopping" }
                    state.value = false
                    monitor.unsubscribe(ApplicationStarted) {}
                    monitor.unsubscribe(ApplicationStopped) {}
                }
                start(wait = false)
            }
        }
    }

    override fun stop() {
        discoveryManager.unregisterService()
        server?.stop(gracePeriodMillis = 1000, timeoutMillis = 5000)
        server = null
        state.value = false
    }

    private companion object {
        private val TAG = DefaultNetworkServerRepository::class
    }
}

private fun Application.module() {
    install(WebSockets) {
        pingPeriod = 5.seconds
        timeout = 3.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    routing {
        webSocket(Endpoints.STREAM_AUDIO) { audioStreamingServerWebSocket() }
        webSocket(Endpoints.STREAM_VIDEO) { videoStreamingServerWebSocket() }
    }
}
