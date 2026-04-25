package org.vpilo.babymonitor.network.relay

import io.ktor.client.plugins.websocket.webSocket
import io.ktor.http.HttpMethod
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.network.common.Constants
import org.vpilo.babymonitor.network.common.DiscoveredServer
import org.vpilo.babymonitor.network.common.DiscoveryManager
import org.vpilo.babymonitor.network.common.Endpoints
import org.vpilo.babymonitor.network.common.RelayHandshake
import org.vpilo.babymonitor.network.common.RelaySignals
import org.vpilo.babymonitor.network.common.deriveSharedRelaySecret
import org.vpilo.babymonitor.network.common.relayHttpClient
import java.security.KeyStore
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

class DefaultNetworkRelayRepository(
    private val discoveryManager: DiscoveryManager,
    coroutineContext: CoroutineContext,
) {
    private val scope = CoroutineScope(SupervisorJob() + coroutineContext)
    private var server: EmbeddedServer<*, *>? = null

    private val currentServers = MutableStateFlow<Set<DiscoveredServer>>(emptySet())
    private val remoteServers = MutableStateFlow<Map<String, WebSocketServerSession>>(emptyMap())

    // Key: "$serverId:$endpoint" e.g. "nursery:/video"
    private val pendingRelays =
        ConcurrentHashMap<String, ConcurrentLinkedDeque<CompletableDeferred<WebSocketServerSession?>>>()

    fun start() {
        if (server != null) return
        discoveryManager.discoveredServers
            .onEach { currentServers.value = it }
            .launchIn(scope)

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
        relayHttpClient.close()
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
            handleCameraRegistration()
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

        combine(currentServers, remoteServers) { local, remote ->
            val localNames = local.map { it.id.name }.toSet()
            localNames + remote.keys.filter { it !in localNames }
        }.distinctUntilChanged()
            .collect { names ->
                Logger.i(TAG) { "Server list: $names" }
                send(names.joinToString("\n"))
            }
    }

    private suspend fun DefaultWebSocketServerSession.handleCameraRegistration() {
        setupSession() ?: return

        val cameraName =
            (incoming.receive() as? Frame.Text)
                ?.readText()
                ?.takeIf { it.isNotBlank() && !it.contains('\n') }
                ?: run {
                    close()
                    return
                }

        Logger.i(TAG) { "Camera '$cameraName' registered" }
        remoteServers.update { it + (cameraName to this) }
        try {
            @Suppress("UnusedPrivateProperty")
            for (ignored in incoming) {
                // drain to detect disconnect
            }
        } finally {
            remoteServers.update { it - cameraName }
            pendingRelays.keys
                .filter { it.startsWith("$cameraName:") }
                .forEach { key ->
                    pendingRelays.remove(key)?.forEach { deferred ->
                        deferred.complete(null)
                    }
                }
            Logger.i(TAG) { "Camera '$cameraName' unregistered" }
        }
    }

    @Suppress("ReturnCount")
    private suspend fun DefaultWebSocketServerSession.handleClientEndpoint(endpoint: String) {
        setupSession() ?: return

        val id =
            serverId
                ?: run {
                    close()
                    return
                }

        val localAddress =
            currentServers.value
                .firstOrNull { it.id.name == id }
                ?.addresses
                ?.firstOrNull()

        if (localAddress != null) {
            relayHttpClient.webSocket(
                method = HttpMethod.Get,
                host = localAddress.hostAddress,
                port = Constants.WEBSOCKET_PORT,
                path = endpoint,
            ) {
                pingInterval = Constants.WEBSOCKET_PING_PERIOD
                timeout = Constants.WEBSOCKET_TIMEOUT

                runProxySession(client = this@handleClientEndpoint, server = this)
            }
        } else {
            val registrationSession =
                remoteServers.value[id]
                    ?: run {
                        send(Frame.Text("Server not found: $id"))
                        close()
                        return
                    }
            rendezvousWithRemoteCamera(id, endpoint, registrationSession)
        }
    }

    private suspend fun DefaultWebSocketServerSession.rendezvousWithRemoteCamera(
        serverId: String,
        endpoint: String,
        registrationSession: WebSocketServerSession,
    ) {
        val relayKey = "$serverId:$endpoint"
        val cameraArrived = CompletableDeferred<WebSocketServerSession?>()
        pendingRelays.computeIfAbsent(relayKey) { ConcurrentLinkedDeque() }.addLast(cameraArrived)

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
            pendingRelays[relayKey]?.remove(cameraArrived)
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
                        coroutineContext.cancel()
                    }
                }
                launch {
                    try {
                        for (frame in server.incoming) {
                            client.send(frame)
                        }
                    } finally {
                        coroutineContext.cancel()
                    }
                }
            }
        } catch (_: CancellationException) {
            // Normal close means one side dropped
        }
        server.close()
        client.close()
    }

    @Suppress("ReturnCount")
    private suspend fun DefaultWebSocketServerSession.handleCameraStreamEndpoint(endpoint: String) {
        setupSession() ?: return

        serverId ?: run {
            close()
            return
        }

        val relayKey = "$serverId:$endpoint"
        val deferred = pendingRelays[relayKey]?.pollFirst()
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

    private val WebSocketServerSession.serverId: String?
        get() = call.parameters["serverId"]

    private companion object {
        private val TAG = DefaultNetworkRelayRepository::class
        private const val KEYSTORE_RESOURCE = "relay.p12"
        private const val KEYSTORE_PASSWORD = "babymonitor"
        private const val KEYSTORE_ALIAS = "relay"

        private val SHARED_SECRET: ByteArray by lazy { deriveSharedRelaySecret() }
    }
}
