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
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.network.common.Constants
import org.vpilo.babymonitor.network.common.DiscoveredServer
import org.vpilo.babymonitor.network.common.DiscoveryManager
import org.vpilo.babymonitor.network.common.Endpoints
import org.vpilo.babymonitor.network.common.RelayHandshake
import org.vpilo.babymonitor.network.common.RelaySignals
import org.vpilo.babymonitor.network.common.relayHttpClient
import java.security.KeyStore
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import kotlin.coroutines.CoroutineContext

class DefaultNetworkRelayRepository(
    private val discoveryManager: DiscoveryManager,
    private val config: RelayConfig,
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
                    sslConnector(
                        keyStore = keyStore,
                        keyAlias = KEYSTORE_ALIAS,
                        keyStorePassword = { KEYSTORE_PASSWORD.toCharArray() },
                        privateKeyPassword = { KEYSTORE_PASSWORD.toCharArray() },
                    ) {
                        port = config.port
                    }
                },
                module = { relayModule() },
            ).start(wait = false)

        Logger.i(TAG) { "Relay started on port ${config.port}" }
    }

    private fun Application.relayModule() {
        install(WebSockets) {
            pingPeriod = Constants.WEBSOCKET_PING_PERIOD
            timeout = Constants.WEBSOCKET_TIMEOUT
        }
        routing {
            discoveryRoute()
            clientRoutes()
            serverRoutes()
        }
    }

    private fun Route.discoveryRoute() {
        webSocket("/relay/discovery") {
            Logger.i(TAG) { "Client connected to discovery endpoint" }
            if (!RelayHandshake.await(this, config.secret)) {
                close()
                return@webSocket
            }
            combine(currentServers, remoteServers) { local, remote ->
                val localNames = local.map { it.id.name }.toSet()
                localNames + remote.keys.filter { it !in localNames }
            }.collect { names ->
                Logger.i(TAG) { "Server list: $names" }
                send(names.joinToString("\n"))
            }
        }
    }

    private fun Route.clientRoutes() {
        webSocket("/relay/client${Endpoints.CONTROL}/{serverId}") {
            Logger.i(TAG) { "Monitor '${call.parameters["serverId"]}' connected to control endpoint" }
            handleClientEndpoint(Endpoints.CONTROL)
        }
        webSocket("/relay/client${Endpoints.STREAM_AUDIO}/{serverId}") {
            Logger.i(TAG) { "Monitor '${call.parameters["serverId"]}' connected to audio stream endpoint" }
            handleClientEndpoint(Endpoints.STREAM_AUDIO)
        }
        webSocket("/relay/client${Endpoints.STREAM_VIDEO}/{serverId}") {
            Logger.i(TAG) { "Monitor '${call.parameters["serverId"]}' connected to video stream endpoint" }
            handleClientEndpoint(Endpoints.STREAM_VIDEO)
        }
    }

    private fun Route.serverRoutes() {
        webSocket("/relay/server") {
            Logger.i(TAG) { "Server connected to server endpoint" }
            handleCameraRegistration()
        }
        webSocket("/relay/server${Endpoints.CONTROL}/{serverId}") {
            Logger.i(TAG) { "Server '${call.parameters["serverId"]}' connected to control endpoint" }
            handleCameraStreamEndpoint(Endpoints.CONTROL)
        }
        webSocket("/relay/server${Endpoints.STREAM_AUDIO}/{serverId}") {
            Logger.i(TAG) { "Server '${call.parameters["serverId"]}' connected to audio stream endpoint" }
            handleCameraStreamEndpoint(Endpoints.STREAM_AUDIO)
        }
        webSocket("/relay/server${Endpoints.STREAM_VIDEO}/{serverId}") {
            Logger.i(TAG) { "Server '${call.parameters["serverId"]}' connected to video stream endpoint" }
            handleCameraStreamEndpoint(Endpoints.STREAM_VIDEO)
        }
    }

    private suspend fun DefaultWebSocketServerSession.handleCameraRegistration() {
        if (!RelayHandshake.await(this, config.secret)) {
            close()
            return
        }
        val cameraName = (incoming.receive() as? Frame.Text)?.readText()
        if (cameraName == null) {
            close()
            return
        }
        remoteServers.update { it + (cameraName to this) }
        Logger.i(TAG) { "Camera '$cameraName' registered" }
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
        if (!RelayHandshake.await(this, config.secret)) {
            close()
            return
        }
        val serverId = call.parameters["serverId"]
        if (serverId == null) {
            close()
            return
        }

        val localAddress =
            currentServers.value
                .firstOrNull { it.id.name == serverId }
                ?.addresses
                ?.firstOrNull()

        if (localAddress != null) {
            relayHttpClient.webSocket(
                method = HttpMethod.Get,
                host = localAddress.hostAddress,
                port = Constants.WEBSOCKET_PORT,
                path = endpoint,
            ) {
                ProxySession.run(client = this@handleClientEndpoint, server = this)
            }
            return
        }

        val registrationSession = remoteServers.value[serverId]
        if (registrationSession == null) {
            send(Frame.Text("Server not found: $serverId"))
            close()
            return
        }

        rendezvousWithRemoteCamera(serverId, endpoint, registrationSession)
    }

    private suspend fun DefaultWebSocketServerSession.rendezvousWithRemoteCamera(
        serverId: String,
        endpoint: String,
        registrationSession: WebSocketServerSession,
    ) {
        val relayKey = "$serverId:$endpoint"
        val cameraArrived = CompletableDeferred<WebSocketServerSession?>()
        pendingRelays.getOrPut(relayKey) { ConcurrentLinkedDeque() }.addLast(cameraArrived)

        try {
            registrationSession.send(signalFor(endpoint))

            val cameraSession =
                withTimeoutOrNull(Constants.WEBSOCKET_TIMEOUT.inWholeMilliseconds) {
                    cameraArrived.await()
                }
            if (cameraSession == null) {
                close(CloseReason(CloseReason.Codes.TRY_AGAIN_LATER, "Camera unavailable"))
                return
            }
            ProxySession.run(client = this, server = cameraSession)
        } finally {
            pendingRelays[relayKey]?.remove(cameraArrived)
        }
    }

    @Suppress("ReturnCount")
    private suspend fun DefaultWebSocketServerSession.handleCameraStreamEndpoint(endpoint: String) {
        if (!RelayHandshake.await(this, config.secret)) {
            close()
            return
        }
        val serverId = call.parameters["serverId"]
        if (serverId == null) {
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
        closeReason.await()
    }

    fun stop() {
        server?.stop(
            gracePeriodMillis = Constants.SERVER_STOP_GRACE_PERIOD.inWholeMilliseconds,
            timeoutMillis = Constants.SERVER_STOP_GRACE_PERIOD.inWholeMilliseconds,
        )
        server = null
        relayHttpClient.close()
        Logger.i(TAG) { "Relay stopped" }
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

    private companion object {
        private val TAG = DefaultNetworkRelayRepository::class
        private const val KEYSTORE_RESOURCE = "relay.p12"
        private const val KEYSTORE_PASSWORD = "babymonitor"
        private const val KEYSTORE_ALIAS = "relay"
    }
}
