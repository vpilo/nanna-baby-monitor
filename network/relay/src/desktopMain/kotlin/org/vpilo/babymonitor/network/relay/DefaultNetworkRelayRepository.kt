package org.vpilo.babymonitor.network.relay

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.http.HttpMethod
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.network.common.Constants
import org.vpilo.babymonitor.network.common.DiscoveredServer
import org.vpilo.babymonitor.network.common.DiscoveryManager
import org.vpilo.babymonitor.network.common.Endpoints
import org.vpilo.babymonitor.network.common.RelayHandshake
import java.net.InetAddress
import java.security.KeyStore
import kotlin.coroutines.CoroutineContext
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets

class DefaultNetworkRelayRepository(
    private val discoveryManager: DiscoveryManager,
    private val config: RelayConfig,
    coroutineContext: CoroutineContext,
) {
    private val scope = CoroutineScope(SupervisorJob() + coroutineContext)
    private var server: EmbeddedServer<*, *>? = null

    private val currentServers = MutableStateFlow<Set<DiscoveredServer>>(emptySet())

    private val localClient =
        HttpClient(ClientCIO) {
            install(ClientWebSockets)
        }

    fun start() {
        discoveryManager.discoveredServers
            .onEach { currentServers.value = it }
            .launchIn(scope)

        val keyStore = loadKeyStore()

        server =
            embeddedServer(
                factory = CIO,
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
                module = {
                    install(WebSockets) {
                        pingPeriodMillis = 30_000L
                        timeoutMillis = 10_000L
                    }
                    routing {
                        webSocket("/relay/discovery") {
                            if (!RelayHandshake.await(this, config.secret)) {
                                close()
                                return@webSocket
                            }
                            currentServers.collect { servers ->
                                val names = servers.joinToString("\n") { it.id.name }
                                send(names)
                            }
                        }

                        webSocket("/relay${Endpoints.CONTROL}/{serverId}") {
                            proxyEndpoint(Endpoints.CONTROL)
                        }

                        webSocket("/relay${Endpoints.STREAM_AUDIO}/{serverId}") {
                            proxyEndpoint(Endpoints.STREAM_AUDIO)
                        }

                        webSocket("/relay${Endpoints.STREAM_VIDEO}/{serverId}") {
                            proxyEndpoint(Endpoints.STREAM_VIDEO)
                        }
                    }
                },
            ).start(wait = false)

        Logger.i(TAG) { "Relay started on port ${config.port}" }
    }

    private suspend fun WebSocketServerSession.proxyEndpoint(endpoint: String) {
        if (!RelayHandshake.await(this, config.secret)) {
            close()
            return
        }
        val serverAddress =
            resolveServerAddress() ?: run {
                close()
                return
            }

        localClient.webSocket(
            method = HttpMethod.Get,
            host = serverAddress.hostAddress,
            port = Constants.WEBSOCKET_PORT,
            path = endpoint,
        ) {
            ProxySession.run(client = this@proxyEndpoint, server = this)
        }
    }

    private suspend fun WebSocketServerSession.resolveServerAddress(): InetAddress? {
        val serverId = call.parameters["serverId"] ?: return null
        val serverAddress =
            currentServers.value
                .firstOrNull { it.id.name == serverId }
                ?.addresses
                ?.firstOrNull()
        if (serverAddress == null) {
            send(Frame.Text("Server not found: $serverId"))
        }
        return serverAddress
    }

    fun stop() {
        server?.stop(gracePeriodMillis = 1_000, timeoutMillis = 5_000)
        server = null
        localClient.close()
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

    private companion object {
        private val TAG = DefaultNetworkRelayRepository::class
        private const val KEYSTORE_RESOURCE = "relay.p12"
        private const val KEYSTORE_PASSWORD = "babymonitor"
        private const val KEYSTORE_ALIAS = "relay"
    }
}
