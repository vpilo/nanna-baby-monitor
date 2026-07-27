package org.vpilo.babymonitor.relay

import io.ktor.network.tls.certificates.buildKeyStore
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
import org.vpilo.babymonitor.model.AppRole
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.model.repository.toDeviceId
import org.vpilo.babymonitor.network.model.Constants
import org.vpilo.babymonitor.network.model.Endpoints
import org.vpilo.babymonitor.network.model.RelaySignals
import org.vpilo.babymonitor.network.model.transport.asTransportString
import org.vpilo.babymonitor.network.model.transport.fromTransportString
import org.vpilo.babymonitor.network.security.crypto.sha256Fingerprint
import org.vpilo.babymonitor.network.security.relay.verifyRelayAccess
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.TimeUnit

private data class PendingSessionKey(
    val serverId: DeviceId,
    val endpoint: String,
)

class DefaultNetworkRelayRepository {
    private var server: EmbeddedServer<*, *>? = null

    private lateinit var device: Device.Relay

    private lateinit var accessKey: ByteArray

    /**
     * Fingerprint of the certificate served by this relay.
     */
    private lateinit var certificateFingerprint: String

    private val remoteServers = MutableStateFlow<Map<Device.RemoteServer, WebSocketServerSession>>(emptyMap())

    private val pendingRelays =
        ConcurrentHashMap<PendingSessionKey, ConcurrentLinkedDeque<CompletableDeferred<WebSocketServerSession?>>>()

    fun start(
        device: Device.Relay,
        accessKey: ByteArray,
    ) {
        if (server != null) return

        this.device = device
        this.accessKey = accessKey

        // Regenerated on every start: nothing pins this certificate, it only has to be stable for the lifetime
        // of the process so that connectors and relay hash the same bytes during the access handshake.
        val keyStore =
            buildKeyStore {
                certificate(RELAY_KEY_ALIAS) {
                    password = RELAY_KEYSTORE_PASSWORD
                    domains = listOf(Constants.TLS_SERVER_NAME, Constants.SERVICES_LISTEN_ADDRESS)
                }
            }
        certificateFingerprint = (keyStore.getCertificate(RELAY_KEY_ALIAS) as X509Certificate).sha256Fingerprint()

        server =
            embeddedServer(
                factory = Netty,
                configure = {
                    sslConnector(
                        keyStore = keyStore,
                        keyAlias = RELAY_KEY_ALIAS,
                        keyStorePassword = { RELAY_KEYSTORE_PASSWORD.toCharArray() },
                        privateKeyPassword = { RELAY_KEYSTORE_PASSWORD.toCharArray() },
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

    // NOTE: Pairing is not allowed nor possible via relay.
    private fun Route.clientRoutes() {
        webSocket(Endpoints.Relay.CLIENT_DISCOVERY) {
            handleDiscovery()
        }
        webSocket("${Endpoints.Relay.CLIENT_CONTROL}/{serverId}") {
            handleClientEndpoint(Endpoints.CONTROL, Endpoints.Relay.CLIENT_CONTROL)
        }
        webSocket("${Endpoints.Relay.CLIENT_AUDIO}/{serverId}") {
            handleClientEndpoint(Endpoints.STREAM_AUDIO, Endpoints.Relay.CLIENT_AUDIO)
        }
        webSocket("${Endpoints.Relay.CLIENT_VIDEO}/{serverId}") {
            handleClientEndpoint(Endpoints.STREAM_VIDEO, Endpoints.Relay.CLIENT_VIDEO)
        }
    }

    // NOTE: Pairing is not allowed nor possible via relay.
    private fun Route.serverRoutes() {
        webSocket(Endpoints.Relay.SERVER_REGISTRATION) {
            handleServerRegistration()
        }
        webSocket("${Endpoints.Relay.SERVER_CONTROL}/{serverId}") {
            handleCameraStreamEndpoint(Endpoints.CONTROL, Endpoints.Relay.SERVER_CONTROL)
        }
        webSocket("${Endpoints.Relay.SERVER_AUDIO}/{serverId}") {
            handleCameraStreamEndpoint(Endpoints.STREAM_AUDIO, Endpoints.Relay.SERVER_AUDIO)
        }
        webSocket("${Endpoints.Relay.SERVER_VIDEO}/{serverId}") {
            handleCameraStreamEndpoint(Endpoints.STREAM_VIDEO, Endpoints.Relay.SERVER_VIDEO)
        }
    }

    private suspend fun DefaultWebSocketServerSession.handleDiscovery() {
        setupSession(AppRole.CLIENT, Endpoints.Relay.CLIENT_DISCOVERY) ?: return

        Logger.i(TAG) { "Client connected to discovery endpoint" }
        remoteServers.collect { set ->
            val servers = set.keys.map { it.asTransportString() }
            send(servers.joinToString("\n"))
        }
    }

    private suspend fun DefaultWebSocketServerSession.handleServerRegistration() {
        setupSession(AppRole.SERVER, Endpoints.Relay.SERVER_REGISTRATION) ?: return

        Logger.i(TAG) { "Server connected to registration endpoint" }
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
    private suspend fun DefaultWebSocketServerSession.handleClientEndpoint(
        endpoint: String,
        relayEndpoint: String,
    ) {
        setupSession(AppRole.CLIENT, relayEndpoint) ?: return

        Logger.i(TAG) { "Client connected to $endpoint for ${call.parameters["serverId"]}" }
        val id =
            serverId
                ?: run {
                    Logger.w(TAG) { "Closing client connection to $endpoint, invalid server id" }
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
                    Logger.w(TAG) { "Closing client connection to $endpoint, cannot find session" }
                    close(reason = CloseReason(CloseReason.Codes.PROTOCOL_ERROR, "Server not found: $id"))
                    return
                }
        rendezvousWithRemoteCamera(id, endpoint, registrationSession)
    }

    private suspend fun DefaultWebSocketServerSession.rendezvousWithRemoteCamera(
        serverId: DeviceId,
        endpoint: String,
        registrationSession: WebSocketServerSession,
    ) {
        Logger.d(TAG) { "Waiting rendezvous for $serverId on $endpoint" }
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
                close(CloseReason(CloseReason.Codes.TRY_AGAIN_LATER, "Server unavailable"))
                return
            }
            Logger.d(TAG) { "Proxying $endpoint for $serverId" }
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
    private suspend fun DefaultWebSocketServerSession.handleCameraStreamEndpoint(
        endpoint: String,
        relayEndpoint: String,
    ) {
        setupSession(AppRole.SERVER, relayEndpoint) ?: return

        Logger.i(TAG) { "Server connected to $endpoint for ${call.parameters["serverId"]}" }
        val id =
            serverId
                ?: run {
                    Logger.w(TAG) { "Closing server connection to $endpoint, invalid server id" }
                    close(reason = CloseReason(CloseReason.Codes.PROTOCOL_ERROR, "Missing serverId"))
                    return
                }

        val pendingSessionKey = PendingSessionKey(id, endpoint)
        val deferred = pendingRelays[pendingSessionKey]?.pollFirst()
        if (deferred == null) {
            Logger.w(TAG) { "Closing server connection to $endpoint, no pending clients found for $pendingSessionKey" }
            close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "No pending clients"))
            return
        }
        deferred.complete(this)
        try {
            closeReason.await()
        } finally {
            close()
        }
    }

    private fun signalFor(endpoint: String): String =
        when (endpoint) {
            Endpoints.CONTROL -> RelaySignals.CONTROL
            Endpoints.STREAM_AUDIO -> RelaySignals.AUDIO
            Endpoints.STREAM_VIDEO -> RelaySignals.VIDEO
            else -> error("Unknown endpoint: $endpoint")
        }

    /**
     * The gate every endpoint passes through: no route does anything before the peer has proven it holds the
     * relay passphrase.
     *
     * The nullable Unit is only to allow single-line early returns on failure.
     */
    private suspend fun DefaultWebSocketServerSession.setupSession(
        role: AppRole,
        relayEndpoint: String,
    ): Unit? {
        pingInterval = Constants.WEBSOCKET_PING_PERIOD
        timeout = Constants.WEBSOCKET_TIMEOUT

        if (!verifyRelayAccess(accessKey, role, relayEndpoint, certificateFingerprint)) {
            Logger.w(TAG) { "Refused unauthenticated $role connection to $relayEndpoint" }
            // Deliberately unspecific: a caller guessing passphrases learns nothing from the close reason.
            close(reason = CloseReason(CloseReason.Codes.NORMAL, ""))
            return null
        }
        return Unit
    }

    private val WebSocketServerSession.serverId: DeviceId?
        get() =
            call.parameters["serverId"]
                ?.toDeviceId()

    private companion object {
        private const val RELAY_KEY_ALIAS = "babymonitor-relay"
        private const val RELAY_KEYSTORE_PASSWORD = "babymonitor"

        private val TAG = DefaultNetworkRelayRepository::class
    }
}
