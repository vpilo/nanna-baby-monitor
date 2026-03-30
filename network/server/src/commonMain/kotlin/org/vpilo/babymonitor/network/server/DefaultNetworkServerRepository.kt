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
import io.ktor.websocket.CloseReason
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.CaptureMode
import org.vpilo.babymonitor.model.repository.NetworkServerRepository
import org.vpilo.babymonitor.model.repository.ServerState
import org.vpilo.babymonitor.network.common.Constants
import org.vpilo.babymonitor.network.common.DiscoveryManager
import org.vpilo.babymonitor.network.common.Endpoints
import org.vpilo.babymonitor.network.server.websockets.audioStreamingServerWebSocket
import org.vpilo.babymonitor.network.server.websockets.controlServerWebSocket
import org.vpilo.babymonitor.network.server.websockets.videoStreamingServerWebSocket
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds

internal class DefaultNetworkServerRepository(
    private val discoveryManager: DiscoveryManager,
    private val coroutineContext: CoroutineContext,
) : NetworkServerRepository {
    private var server: EmbeddedServer<*, *>? = null

    private val activeAudioSessions = mutableListOf<WebSocketSession>()
    private val activeVideoSessions = mutableListOf<WebSocketSession>()

    private val state = MutableStateFlow(ServerState(isAvailable = false, captureMode = CaptureMode.AUDIO_AND_VIDEO))
    override val serverStateFlow: Flow<ServerState> = state.asStateFlow()

    private val currentCaptureMode: CaptureMode
        get() = state.value.captureMode

    override suspend fun start() {
        if (server != null) {
            return
        }

        discoveryManager.registerService()

        withContext(coroutineContext) {
            embeddedServer(
                factory = CIO,
                module = { serverModule() },
                host = Constants.SERVICES_LISTEN_ADDRESS,
                port = Constants.WEBSOCKET_PORT,
            ).apply {
                server = this

                monitor.subscribe(ServerReady) {
                    Logger.i(TAG) { "Server is ready at ${Constants.SERVICES_LISTEN_ADDRESS}" }
                    state.update { it.copy(isAvailable = true) }
                }
                monitor.subscribe(ApplicationStopped) {
                    Logger.i(TAG) { "Server is stopping" }
                    state.update { it.copy(isAvailable = false) }
                    monitor.unsubscribe(ApplicationStarted) {}
                    monitor.unsubscribe(ApplicationStopped) {}
                }
                start(wait = false)
            }
        }
    }

    override suspend fun stop() {
        withContext(coroutineContext) {
            Logger.i(TAG) { "Requested server stop" }
            activeAudioSessions.closeAll()
            activeVideoSessions.closeAll()
            discoveryManager.unregisterService()
            server?.stop(gracePeriodMillis = 1000, timeoutMillis = 5000)
            server = null
            state.update { it.copy(isAvailable = false) }
        }
    }

    override suspend fun setCaptureMode(mode: CaptureMode) {
        when (mode) {
            CaptureMode.VIDEO_ONLY -> activeAudioSessions.closeAll()
            CaptureMode.AUDIO_ONLY -> activeVideoSessions.closeAll()
            else -> {
                /* Nothing to do */
            }
        }

        Logger.i(TAG) { "Requested update to $mode" }
        state.update { it.copy(captureMode = mode) }
    }

    private suspend fun MutableList<WebSocketSession>.closeAll() {
        forEach { it.close(CloseReason(CloseReason.Codes.GOING_AWAY, "")) }
        clear()
    }

    private fun Application.serverModule() {
        install(WebSockets) {
            pingPeriod = 5.seconds
            timeout = 3.seconds
            maxFrameSize = Long.MAX_VALUE
            masking = false
        }

        routing {
            webSocket(Endpoints.CONTROL) {
                controlServerWebSocket()
            }
            webSocket(Endpoints.STREAM_AUDIO) {
                if (currentCaptureMode == CaptureMode.VIDEO_ONLY) {
                    close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Audio streaming is disabled"))
                    return@webSocket
                }
                activeAudioSessions.add(this)
                try {
                    audioStreamingServerWebSocket()
                } finally {
                    Logger.i(TAG) { "Closed audio session" }
                    activeAudioSessions.remove(this)
                }
            }
            webSocket(Endpoints.STREAM_VIDEO) {
                if (currentCaptureMode == CaptureMode.AUDIO_ONLY) {
                    close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Video streaming is disabled"))
                    return@webSocket
                }
                activeVideoSessions.add(this)
                try {
                    videoStreamingServerWebSocket()
                } finally {
                    Logger.i(TAG) { "Closed video session" }
                    activeVideoSessions.remove(this)
                }
            }
        }
    }

    private companion object {
        private val TAG = DefaultNetworkServerRepository::class
    }
}
