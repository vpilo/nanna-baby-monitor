package org.vpilo.babymonitor.relay

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
import io.ktor.server.netty.Netty
import io.ktor.server.routing.Route
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.pingInterval
import io.ktor.websocket.readText
import io.ktor.websocket.send
import io.ktor.websocket.timeout
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.model.repository.toDeviceId
import org.vpilo.babymonitor.network.common.Constants
import org.vpilo.babymonitor.network.common.Endpoints
import org.vpilo.babymonitor.network.common.RelayHandshake
import org.vpilo.babymonitor.network.common.RelaySignals
import org.vpilo.babymonitor.network.common.deriveSharedRelaySecret
import org.vpilo.babymonitor.network.common.discovery.ktx.asTransportString
import org.vpilo.babymonitor.network.common.discovery.ktx.fromTransportString
import java.security.KeyStore
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.TimeUnit

private class PendingSessionKey(
    val serverId: DeviceId,
    // The presence of `endpoint` allows distinguishing control/audio/video pending sessions.
    @Suppress("unused")
    val endpoint: String,
)

class DefaultNetworkRelayRepository {
    private var server: EmbeddedServer<*, *>? = null

    private lateinit var device: Device.Relay

    private val remoteServers = MutableStateFlow<Map<Device.RemoteServer, WebSocketServerSession>>(emptyMap())

    private val pendingRelays =
        ConcurrentHashMap<PendingSessionKey, ConcurrentLinkedDeque<CompletableDeferred<WebSocketServerSession?>>>()

    fun start(device: Device.Relay) {
        if (server != null) return

        this.device = device
        val keyStore = loadKeyStore()

        server =
            embeddedServer(
                factory = Netty,
                configure = {
                    val getPassword = { KEYSTORE_PASSWORD.toCharArray() }
                    sslConnector(
                        keyStore = keyStore,
                        keyAlias = KEYSTORE_ALIAS,
                        keyStorePassword = getPassword,
                        privateKeyPassword = getPassword,
                    ) {
                        host = Constants.SERVICES_LISTEN_ADDRESS
                        port = Constants.RELAY_PORT
                    }
                },
                module = { relayModule() },
            ).start(wait = false)

        Logger.i(TAG) { "Relay started on ${Constants.SERVICES_LISTEN_ADDRESS}:${Constants.RELAY_PORT}" }
    }

    fun stop() {
        server?.stop(
            shutdownGracePeriod = Constants.SERVER_STOP_GRACE_PERIOD.inWholeMilliseconds,
            shutdownTimeout = Constants.SERVER_STOP_GRACE_PERIOD.inWholeMilliseconds,
            timeUnit = TimeUnit.MILLISECONDS,
        )
        server = null
        Logger.i(TAG) { "Relay stopped" }
    }

    private fun Application.relayModule() {
        install(WebSockets) {
            pingPeriod = Constants.WEBSOCKET_PING_PERIOD
            timeout = Constants.WEBSOCKET_TIMEOUT
        }
        routing {
            clientRoutes()
            serverRoutes()
        }
    }

    private fun Route.clientRoutes() {
        webSocket(Endpoints.Relay.CLIENT_DISCOVERY) {
            Logger.i(TAG) { "Client connected to discovery endpoint" }
            handleDiscovery()
        }
        webSocket("${Endpoints.Relay.CLIENT_CONTROL}/{serverId}") {
            Logger.i(TAG) { "Monitor '$serverId' connected to control endpoint" }
            handleClientEndpoint(Endpoints.CONTROL)
        }
        webSocket("${Endpoints.Relay.CLIENT_AUDIO}/{serverId}") {
            Logger.i(TAG) { "Monitor '$serverId' connected to audio stream endpoint" }
            handleClientEndpoint(Endpoints.STREAM_AUDIO)
        }
        webSocket("${Endpoints.Relay.CLIENT_VIDEO}/{serverId}") {
            Logger.i(TAG) { "Monitor '$serverId' connected to video stream endpoint" }
            handleClientEndpoint(Endpoints.STREAM_VIDEO)
        }
    }

    private fun Route.serverRoutes() {
        webSocket(Endpoints.Relay.SERVER_REGISTRATION) {
            Logger.i(TAG) { "Server connected to server endpoint" }
            handleServerRegistration()
        }
        webSocket("${Endpoints.Relay.SERVER_CONTROL}/{serverId}") {
            Logger.i(TAG) { "Server '$serverId' connected to control endpoint" }
            handleCameraStreamEndpoint(Endpoints.CONTROL)
        }
        webSocket("${Endpoints.Relay.SERVER_AUDIO}/{serverId}") {
            Logger.i(TAG) { "Server '$serverId' connected to audio stream endpoint" }
            handleCameraStreamEndpoint(Endpoints.STREAM_AUDIO)
        }
        webSocket("${Endpoints.Relay.SERVER_VIDEO}/{serverId}") {
            Logger.i(TAG) { "Server '$serverId' connected to video stream endpoint" }
            handleCameraStreamEndpoint(Endpoints.STREAM_VIDEO)
        }
    }

    private suspend fun DefaultWebSocketServerSession.handleDiscovery() {
        setupSession() ?: return

        remoteServers.collect { set ->
            val servers = set.keys.map { it.asTransportString() }
            send(servers.joinToString("\n"))
        }
    }

    private suspend fun DefaultWebSocketServerSession.handleServerRegistration() {
        setupSession() ?: return

        val registration =
            (incoming.receive() as? Frame.Text)
                ?.readText()
                ?.takeIf { it.isNotBlank() && !it.contains('\n') }
                ?: run {
                    close()
                    return
                }
        val server = Device.RemoteServer.fromTransportString(registration)
        if (server == null) {
            Logger.e(TAG) { "Invalid server registration: '$registration'" }
            close(reason = CloseReason(CloseReason.Codes.PROTOCOL_ERROR, "Invalid registration"))
            return
        }
        if (server.relayHost != device.relayHost) {
            Logger.e(TAG) { "Invalid server registration for different relay: $server" }
            close(reason = CloseReason(CloseReason.Codes.PROTOCOL_ERROR, "Invalid registration for this relay"))
            return
        }

        remoteServers.update { it + (server to this) }
        Logger.i(TAG) { "Registered: $server" }
        try {
            @Suppress("UnusedPrivateProperty", "ControlFlowWithEmptyBody", "unused")
            for (ignored in incoming) {
                // drain to detect disconnect
            }
        } finally {
            remoteServers.update { it - server }
            pendingRelays
                .filter { (key, _) -> key.serverId == server.id }
                .forEach { (key, _) ->
                    pendingRelays
                        .remove(key)
                        ?.forEach { deferred -> deferred.complete(null) }
                }
            Logger.i(TAG) { "Unregistered: $server" }
        }
    }

    @Suppress("ReturnCount")
    private suspend fun DefaultWebSocketServerSession.handleClientEndpoint(endpoint: String) {
        setupSession() ?: return

        val id =
            serverId
                ?: run {
                    close(reason = CloseReason(CloseReason.Codes.PROTOCOL_ERROR, "Missing serverId"))
                    return
                }

        val registrationSession =
            remoteServers.value
                .firstNotNullOfOrNull { (server, session) ->
                    if (server.id != id) return@firstNotNullOfOrNull null
                    session
                }
                ?: run {
                    send(Frame.Text("Server not found: $id"))
                    close()
                    return
                }
        rendezvousWithRemoteCamera(id, endpoint, registrationSession)
    }

    private suspend fun DefaultWebSocketServerSession.rendezvousWithRemoteCamera(
        serverId: DeviceId,
        endpoint: String,
        registrationSession: WebSocketServerSession,
    ) {
        val pendingSessionKey = PendingSessionKey(serverId, endpoint)
        val cameraArrived = CompletableDeferred<WebSocketServerSession?>()
        pendingRelays.computeIfAbsent(pendingSessionKey) { ConcurrentLinkedDeque() }.addLast(cameraArrived)

        try {
            registrationSession.send(signalFor(endpoint))

            val cameraSession =
                withTimeoutOrNull(Constants.RELAY_RENDEZVOUS_TIMEOUT) {
                    cameraArrived.await()
                }
            if (cameraSession == null) {
                close(CloseReason(CloseReason.Codes.TRY_AGAIN_LATER, "Camera unavailable"))
                return
            }
            runProxySession(client = this, server = cameraSession)
        } finally {
            pendingRelays[pendingSessionKey]?.remove(cameraArrived)
        }
    }

    suspend fun runProxySession(
        client: WebSocketSession,
        server: WebSocketSession,
    ) {
        try {
            coroutineScope {
                launch {
                    try {
                        for (frame in client.incoming) {
                            server.send(frame)
                        }
                    } finally {
                        server.close()
                    }
                }
                launch {
                    try {
                        for (frame in server.incoming) {
                            client.send(frame)
                        }
                    } finally {
                        client.close()
                    }
                }
            }
        } finally {
            server.close()
            client.close()
        }
    }

    @Suppress("ReturnCount")
    private suspend fun DefaultWebSocketServerSession.handleCameraStreamEndpoint(endpoint: String) {
        setupSession() ?: return

        val id =
            serverId
                ?: run {
                    close(reason = CloseReason(CloseReason.Codes.PROTOCOL_ERROR, "Missing serverId"))
                    return
                }

        val pendingSessionKey = PendingSessionKey(id, endpoint)
        val deferred = pendingRelays[pendingSessionKey]?.pollFirst()
        if (deferred == null) {
            close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "No pending client for $serverId/$endpoint"))
            return
        }
        deferred.complete(this)
        try {
            closeReason.await()
        } finally {
            close()
        }
    }

    private fun loadKeyStore(): KeyStore {
        val stream =
            checkNotNull(
                DefaultNetworkRelayRepository::class.java.classLoader.getResourceAsStream(KEYSTORE_RESOURCE),
            ) { "relay.p12 not found in resources" }
        return stream.use { s ->
            KeyStore.getInstance("PKCS12").apply {
                load(s, KEYSTORE_PASSWORD.toCharArray())
            }
        }
    }

    private fun signalFor(endpoint: String): String =
        when (endpoint) {
            Endpoints.CONTROL -> RelaySignals.CONTROL
            Endpoints.STREAM_AUDIO -> RelaySignals.AUDIO
            Endpoints.STREAM_VIDEO -> RelaySignals.VIDEO
            else -> error("Unknown endpoint: $endpoint")
        }

    // The nullable Unit is only to allow single-line early returns on failure.
    private suspend fun DefaultWebSocketServerSession.setupSession(): Unit? {
        if (!RelayHandshake.await(this, SHARED_SECRET)) {
            close()
            return null
        }
        pingInterval = Constants.WEBSOCKET_PING_PERIOD
        timeout = Constants.WEBSOCKET_TIMEOUT
        return Unit
    }

    private val WebSocketServerSession.serverId: DeviceId?
        get() =
            call.parameters["serverId"]
                ?.toDeviceId()

    private companion object {
        private val TAG = DefaultNetworkRelayRepository::class
        private const val KEYSTORE_RESOURCE = "relay.p12"
        private const val KEYSTORE_PASSWORD = "babymonitor"
        private const val KEYSTORE_ALIAS = "relay"

        private val SHARED_SECRET: ByteArray by lazy { deriveSharedRelaySecret() }
    }
}
