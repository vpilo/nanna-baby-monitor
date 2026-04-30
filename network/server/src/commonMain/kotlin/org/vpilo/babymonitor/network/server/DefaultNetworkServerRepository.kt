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
import io.ktor.websocket.pingInterval
import io.ktor.websocket.timeout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.CaptureMode
import org.vpilo.babymonitor.model.repository.DeviceStateRepository
import org.vpilo.babymonitor.model.repository.NetworkServerRepository
import org.vpilo.babymonitor.model.repository.ServerState
import org.vpilo.babymonitor.network.common.Constants
import org.vpilo.babymonitor.network.common.DiscoveryManager
import org.vpilo.babymonitor.network.common.DiscoveryManagerState
import org.vpilo.babymonitor.network.common.Endpoints
import org.vpilo.babymonitor.network.server.websockets.audioStreamingServerWebSocket
import org.vpilo.babymonitor.network.server.websockets.controlServerWebSocket
import org.vpilo.babymonitor.network.server.websockets.videoStreamingServerWebSocket
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

internal class DefaultNetworkServerRepository(
    private val discoveryManager: DiscoveryManager,
    private val relayRegistration: RelayServerRegistration,
    deviceStateRepository: DeviceStateRepository,
    private val coroutineContext: CoroutineContext,
) : NetworkServerRepository {
    private var server: EmbeddedServer<*, *>? = null
    private var isServerReady = MutableStateFlow(false)

    private val foregroundLink = ServerForegroundServiceLink()

    private val activeAudioSessions = mutableListOf<WebSocketSession>()
    private val activeVideoSessions = mutableListOf<WebSocketSession>()

    private val state = MutableStateFlow(ServerState())
    override val serverStateFlow: Flow<ServerState> = state.asStateFlow()

    private val currentCaptureMode: CaptureMode
        get() = state.value.captureMode

    private val scope: CoroutineScope = CoroutineScope(coroutineContext + SupervisorJob())

    override fun setRelayHost(host: String) {
        relayRegistration.setRelayHost(host)
    }

    override fun setDeviceName(name: String) {
        discoveryManager.setDeviceName(name)
        relayRegistration.setDeviceName(name)
    }

    init {
        combine(
            isServerReady,
            discoveryManager.state,
            relayRegistration.isRegistered,
        ) { isServerReady, discoveryState, isRelayReady ->
            val reportServerAvailable = isServerReady && (discoveryState == DiscoveryManagerState.ServiceRegistered || isRelayReady)
            state.update { it.copy(isAvailable = reportServerAvailable) }
        }.launchIn(scope)

        combine(
            deviceStateRepository.batteryLevel,
            deviceStateRepository.signalQuality,
        ) { batteryLevel, signalQuality ->
            state.update { it.copy(signalQuality = signalQuality, batteryLevel = batteryLevel) }
        }.launchIn(scope)
    }

    override suspend fun start() {
        if (server != null) return

        foregroundLink.start()

        discoveryManager.registerService()

        scope.launch {
            embeddedServer(
                factory = CIO,
                module = { serverModule() },
                host = Constants.SERVICES_LISTEN_ADDRESS,
                port = Constants.WEBSOCKET_PORT,
            ).apply {
                server = this

                monitor.subscribe(ServerReady) {
                    Logger.i(TAG) { "Server is ready at ${Constants.SERVICES_LISTEN_ADDRESS}" }
                    isServerReady.value = true
                }
                monitor.subscribe(ApplicationStopped) {
                    Logger.i(TAG) { "Server is stopping" }
                    isServerReady.value = false
                    monitor.unsubscribe(ApplicationStarted) {}
                    monitor.unsubscribe(ApplicationStopped) {}
                }
                start(wait = false)
            }
        }

        Logger.i(TAG) { "Requested server start" }
    }

    override suspend fun stop() {
        withContext(coroutineContext) {
            Logger.i(TAG) { "Requested server stop" }
            relayRegistration.stop()
            activeAudioSessions.closeAll()
            activeVideoSessions.closeAll()
            discoveryManager.unregisterService()
            server?.stop(
                shutdownGracePeriod = Constants.SERVER_STOP_GRACE_PERIOD.inWholeSeconds,
                shutdownTimeout = Constants.SERVER_STOP_GRACE_PERIOD.inWholeSeconds,
                timeUnit = TimeUnit.SECONDS,
            )
            isServerReady.value = false
            server = null
            foregroundLink.stop()
        }
    }

    override suspend fun setCaptureMode(mode: CaptureMode) {
        when (mode) {
            CaptureMode.VIDEO_ONLY -> {
                activeAudioSessions.closeAll()
            }

            CaptureMode.AUDIO_ONLY -> {
                activeVideoSessions.closeAll()
            }

            else -> {
                // Nothing to do
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
            pingPeriod = Constants.WEBSOCKET_PING_PERIOD
            timeout = Constants.WEBSOCKET_TIMEOUT
            maxFrameSize = Long.MAX_VALUE
            masking = false
        }

        routing {
            webSocket(Endpoints.CONTROL) {
                pingInterval = Constants.WEBSOCKET_PING_PERIOD
                timeout = Constants.WEBSOCKET_TIMEOUT

                try {
                    controlServerWebSocket()
                } finally {
                    Logger.i(TAG) { "Closed control session" }
                }
            }
            webSocket(Endpoints.STREAM_AUDIO) {
                pingInterval = Constants.WEBSOCKET_PING_PERIOD
                timeout = Constants.WEBSOCKET_TIMEOUT

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
                pingInterval = Constants.WEBSOCKET_PING_PERIOD
                timeout = Constants.WEBSOCKET_TIMEOUT

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
