package org.vpilo.babymonitor.network.server

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationEnvironment
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.ServerReady
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
import io.ktor.server.netty.Netty
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
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.AppRole
import org.vpilo.babymonitor.model.CaptureMode
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.model.repository.DeviceStateRepository
import org.vpilo.babymonitor.model.repository.NetworkServerRepository
import org.vpilo.babymonitor.model.repository.PairingWindowState
import org.vpilo.babymonitor.model.repository.ServerState
import org.vpilo.babymonitor.network.common.Constants
import org.vpilo.babymonitor.network.common.Endpoints
import org.vpilo.babymonitor.network.common.ForegroundServiceLink
import org.vpilo.babymonitor.network.common.protocol.runWebSocketCatching
import org.vpilo.babymonitor.network.server.identity.ServerIdentity
import org.vpilo.babymonitor.network.server.pairing.PairingCoordinator
import org.vpilo.babymonitor.network.server.session.ActiveSessionRegistry
import org.vpilo.babymonitor.network.server.websockets.audioStreamingServerWebSocket
import org.vpilo.babymonitor.network.server.websockets.controlServerWebSocket
import org.vpilo.babymonitor.network.server.websockets.videoStreamingServerWebSocket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

internal class DefaultNetworkServerRepository(
    private val relayRegistration: RelayServerRegistration,
    private val pairingCoordinator: PairingCoordinator,
    private val activeSessionRegistry: ActiveSessionRegistry,
    deviceStateRepository: DeviceStateRepository,
    private val coroutineContext: CoroutineContext,
) : NetworkServerRepository {
    private var server: EmbeddedServer<*, *>? = null
    private var isServerReady = MutableStateFlow(false)

    // The /pair route needs this identity's fingerprint to bind it into the pairing transcript.
    private var serverIdentity: ServerIdentity? = null
    private var self: Device.LocalServer? = null

    private val foregroundLink = ForegroundServiceLink(AppRole.SERVER)

    private val activeControlSessions = CopyOnWriteArrayList<WebSocketSession>()
    private val activeAudioSessions = CopyOnWriteArrayList<WebSocketSession>()
    private val activeVideoSessions = CopyOnWriteArrayList<WebSocketSession>()

    private val state = MutableStateFlow(ServerState())
    override val serverStateFlow: Flow<ServerState> = state.asStateFlow()

    private val currentCaptureMode: CaptureMode
        get() = state.value.captureMode

    private val scope: CoroutineScope = CoroutineScope(coroutineContext + SupervisorJob())

    override fun setRelayHost(host: String) {
        relayRegistration.setRelayHost(host)
    }

    override val pairingState: Flow<PairingWindowState> = pairingCoordinator.state

    override fun startPairingWindow() {
        pairingCoordinator.startPairingWindow(checkNotNull(self) { "Server not started" })
    }

    override fun cancelPairingWindow() {
        pairingCoordinator.cancelPairingWindow()
    }

    override suspend fun closeSessionsForClient(clientId: DeviceId) {
        activeSessionRegistry.closeSessionsForClient(clientId)
    }

    init {
        combine(
            isServerReady,
            relayRegistration.isRegistered,
        ) { isServerReady, isRelayReady ->
            state.update {
                it.copy(
                    isAvailableOnLocalNetwork = isServerReady,
                    isAvailableOnRelay = isRelayReady,
                )
            }
        }.launchIn(scope)

        combine(
            deviceStateRepository.batteryLevel,
            deviceStateRepository.signalQuality,
        ) { batteryLevel, signalQuality ->
            state.update { it.copy(signalQuality = signalQuality, batteryLevel = batteryLevel) }
        }.launchIn(scope)

        deviceStateRepository.isInternetAvailable
            .onEach {
                relayRegistration.setEnabled(it)
            }.launchIn(scope)
    }

    override suspend fun start(self: Device.LocalServer) {
        this.self = self
        if (server != null) return

        foregroundLink.start()
        relayRegistration.identifySelf(self)

        val identity = ServerIdentity.loadOrCreate()
        serverIdentity = identity
        val keyStoreConfig = identity.toKeyStoreConfig()

        scope.launch {
            embeddedServer(
                factory = Netty,
                configure = {
                    sslConnector(
                        keyStore = keyStoreConfig.keyStore,
                        keyAlias = keyStoreConfig.keyAlias,
                        keyStorePassword = { keyStoreConfig.keyStorePassword },
                        privateKeyPassword = { keyStoreConfig.privateKeyPassword },
                    ) {
                        host = Constants.SERVICES_LISTEN_ADDRESS
                        port = Constants.SERVICE_PORT
                    }
                },
                module = { serverModule() },
            ).apply {
                server = this

                val readyHandler: (ApplicationEnvironment) -> Unit = {
                    Logger.i(TAG) { "Server is ready at ${Constants.SERVICES_LISTEN_ADDRESS}" }
                    isServerReady.value = true
                }
                lateinit var stoppedHandler: (Application) -> Unit
                stoppedHandler = {
                    Logger.i(TAG) { "Server is stopping" }
                    isServerReady.value = false
                    monitor.unsubscribe(ServerReady, readyHandler)
                    monitor.unsubscribe(ApplicationStopped, stoppedHandler)
                }
                monitor.subscribe(ServerReady, readyHandler)
                monitor.subscribe(ApplicationStopped, stoppedHandler)
                start(wait = false)
            }
        }

        Logger.i(TAG) { "Requested server start" }
    }

    override suspend fun stop() {
        withContext(coroutineContext) {
            Logger.i(TAG) { "Requested server stop" }
            relayRegistration.stop()
            activeControlSessions.closeAll()
            activeAudioSessions.closeAll()
            activeVideoSessions.closeAll()
            server?.stop(
                shutdownGracePeriod = Constants.SERVER_STOP_GRACE_PERIOD.inWholeSeconds,
                shutdownTimeout = Constants.SERVER_STOP_GRACE_PERIOD.inWholeSeconds,
                timeUnit = TimeUnit.SECONDS,
            )
            server = null
            isServerReady.value = false
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
            // Bound the outgoing frame queue so a stalled TCP connection doesn't enqueue frames infinitely.
            channels {
                outgoing = bounded(MAX_OUTGOING_FRAMES)
            }
        }

        routing {
            webSocket(Endpoints.CONTROL) {
                pingInterval = Constants.WEBSOCKET_PING_PERIOD
                timeout = Constants.WEBSOCKET_TIMEOUT

                activeControlSessions.add(this)
                try {
                    controlServerWebSocket(serverDeviceId = checkNotNull(self).id)
                } finally {
                    Logger.i(TAG) { "Closed control session" }
                    activeControlSessions.remove(this)
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
                    audioStreamingServerWebSocket(serverDeviceId = checkNotNull(self).id)
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
                    videoStreamingServerWebSocket(serverDeviceId = checkNotNull(self).id)
                } finally {
                    Logger.i(TAG) { "Closed video session" }
                    activeVideoSessions.remove(this)
                }
            }
            webSocket(Endpoints.PAIR) {
                pingInterval = Constants.WEBSOCKET_PING_PERIOD
                timeout = Constants.WEBSOCKET_TIMEOUT

                runWebSocketCatching(TAG) {
                    pairingCoordinator.handlePairingSession(this, checkNotNull(serverIdentity) { "Server identity not loaded" })
                }
            }
        }
    }

    private companion object {
        private val TAG = DefaultNetworkServerRepository::class

        private const val MAX_OUTGOING_FRAMES = 32
    }
}
