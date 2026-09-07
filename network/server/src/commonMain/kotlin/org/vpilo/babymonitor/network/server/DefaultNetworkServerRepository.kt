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
import io.ktor.websocket.close
import io.ktor.websocket.pingInterval
import io.ktor.websocket.timeout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.AppRole
import org.vpilo.babymonitor.model.CaptureMode
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.model.repository.DeviceStateRepository
import org.vpilo.babymonitor.model.repository.StreamingAudioSenderRepository
import org.vpilo.babymonitor.model.repository.StreamingVideoSenderRepository
import org.vpilo.babymonitor.network.internal.BackgroundServiceLink
import org.vpilo.babymonitor.network.internal.protocol.runWebSocketCatching
import org.vpilo.babymonitor.network.internal.repository.InternalActiveSessionsRepository
import org.vpilo.babymonitor.network.model.Constants
import org.vpilo.babymonitor.network.model.Endpoints
import org.vpilo.babymonitor.network.model.ServerState
import org.vpilo.babymonitor.network.model.pairing.ServerPairingState
import org.vpilo.babymonitor.network.model.repository.NetworkServerRepository
import org.vpilo.babymonitor.network.model.repository.PairingStorageRepository
import org.vpilo.babymonitor.network.server.identity.ServerIdentity
import org.vpilo.babymonitor.network.server.pairing.PairingCoordinator
import org.vpilo.babymonitor.network.server.websockets.audioStreamingServerWebSocket
import org.vpilo.babymonitor.network.server.websockets.controlServerWebSocket
import org.vpilo.babymonitor.network.server.websockets.videoStreamingServerWebSocket
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

internal class DefaultNetworkServerRepository(
    private val relayRegistration: RelayServerRegistration,
    private val pairingCoordinator: PairingCoordinator,
    private val pairingStorageRepository: PairingStorageRepository,
    private val deviceStateRepository: DeviceStateRepository,
    private val activeSessionsRepository: InternalActiveSessionsRepository,
    private val streamingAudioSenderRepository: StreamingAudioSenderRepository,
    private val streamingVideoSenderRepository: StreamingVideoSenderRepository,
    private val serverStateDataSource: ServerStateDataSource,
    private val coroutineContext: CoroutineContext,
) : NetworkServerRepository {
    private var server: EmbeddedServer<*, *>? = null
    private val isServerReady = MutableStateFlow(false)

    // The /pair route needs this identity's fingerprint to bind it into the pairing transcript.
    private var serverIdentity: ServerIdentity? = null
    private var self: Device.LocalServer? = null

    override val serverStateFlow: Flow<ServerState> = serverStateDataSource.state

    private val currentCaptureMode: CaptureMode
        get() = serverStateDataSource.state.value.captureMode

    private val scope: CoroutineScope = CoroutineScope(coroutineContext + SupervisorJob())

    private val foregroundLink =
        BackgroundServiceLink(AppRole.SERVER) {
            scope.launch { stop() }
        }

    override val pairingState: Flow<ServerPairingState> = pairingCoordinator.state

    override fun startPairingWindow() {
        pairingCoordinator.startPairingWindow(checkNotNull(self) { "Server not started" })
    }

    override fun cancelPairingWindow() {
        pairingCoordinator.cancelPairingWindow()
    }

    override suspend fun start(self: Device.LocalServer) {
        this.self = self
        if (server != null) return

        foregroundLink.start()
        relayRegistration.identifySelf(self)

        val identity = ServerIdentity.loadOrCreate()
        serverIdentity = identity

        scope.launch {
            embeddedServer(
                factory = Netty,
                configure = {
                    // Disabling this also disables ALPN, which seems to be buggy in ktor.
                    enableHttp2 = false
                    val keyStoreConfig = identity.toKeyStoreConfig()
                    sslConnector(
                        keyStore = keyStoreConfig.keyStore,
                        keyAlias = keyStoreConfig.keyAlias,
                        keyStorePassword = { keyStoreConfig.password },
                        privateKeyPassword = { keyStoreConfig.password },
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

        combine(
            isServerReady,
            relayRegistration.isRegistered,
        ) { isServerReady, isRelayReady ->
            serverStateDataSource.update {
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
            serverStateDataSource.update { it.copy(signalQuality = signalQuality, batteryLevel = batteryLevel) }
        }.launchIn(scope)

        deviceStateRepository.isInternetAvailable
            .onEach {
                relayRegistration.setEnabled(it)
            }.launchIn(scope)
    }

    override suspend fun stop() {
        withContext(coroutineContext) {
            Logger.i(TAG) { "Requested server stop" }
            relayRegistration.stop()
            server?.stop(
                shutdownGracePeriod = Constants.SERVER_STOP_GRACE_PERIOD.inWholeSeconds,
                shutdownTimeout = Constants.SERVER_STOP_GRACE_PERIOD.inWholeSeconds,
                timeUnit = TimeUnit.SECONDS,
            )
            server = null
            isServerReady.value = false
            foregroundLink.stop()
            scope.coroutineContext.cancelChildren()
        }
    }

    override suspend fun setCaptureMode(mode: CaptureMode) {
        when (mode) {
            CaptureMode.VIDEO_ONLY,
            CaptureMode.AUDIO_ONLY,
                -> self?.id?.let { activeSessionsRepository.closeSessions(it, wasUnpaired = false) }

            else -> Unit // Nothing to do
        }
        Logger.i(TAG) { "Requested update to $mode" }
        serverStateDataSource.update { it.copy(captureMode = mode) }
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

                try {
                    controlServerWebSocket(
                        serverDeviceId = checkNotNull(self).id,
                        pairingStorageRepository = pairingStorageRepository,
                        activeSessionsRepository = activeSessionsRepository,
                        serverStateDataSource = serverStateDataSource,
                    )
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
                try {
                    audioStreamingServerWebSocket(
                        serverDeviceId = checkNotNull(self).id,
                        pairingStorageRepository = pairingStorageRepository,
                        activeSessionsRepository = activeSessionsRepository,
                        streamingAudioSenderRepository = streamingAudioSenderRepository,
                    )
                } finally {
                    Logger.i(TAG) { "Closed audio session" }
                }
            }
            webSocket(Endpoints.STREAM_VIDEO) {
                pingInterval = Constants.WEBSOCKET_PING_PERIOD
                timeout = Constants.WEBSOCKET_TIMEOUT

                if (currentCaptureMode == CaptureMode.AUDIO_ONLY) {
                    close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Video streaming is disabled"))
                    return@webSocket
                }
                try {
                    videoStreamingServerWebSocket(
                        serverDeviceId = checkNotNull(self).id,
                        pairingStorageRepository = pairingStorageRepository,
                        activeSessionsRepository = activeSessionsRepository,
                        streamingVideoSenderRepository = streamingVideoSenderRepository,
                    )
                } finally {
                    Logger.i(TAG) { "Closed video session" }
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
