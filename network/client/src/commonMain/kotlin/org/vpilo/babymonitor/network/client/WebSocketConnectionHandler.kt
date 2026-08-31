package org.vpilo.babymonitor.network.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSocketException
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.wss
import io.ktor.http.HttpMethod
import io.ktor.websocket.CloseReason
import io.ktor.websocket.pingInterval
import io.ktor.websocket.timeout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.common.ktx.prettify
import org.vpilo.babymonitor.model.AppRole
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.network.model.Constants
import org.vpilo.babymonitor.network.model.Endpoints
import org.vpilo.babymonitor.network.model.usecase.GetRelayConfigurationFlowUseCase
import org.vpilo.babymonitor.network.security.crypto.PinnedTrustManager
import org.vpilo.babymonitor.network.security.relay.relayWss
import java.net.ConnectException
import java.net.InetAddress
import java.net.ProtocolException
import java.security.cert.CertificateException
import kotlin.coroutines.cancellation.CancellationException
import io.ktor.client.plugins.websocket.pingInterval as clientPingInterval

internal class WebSocketConnectionHandler(
    private val device: Device,
    private val endpointPath: String,
    private val sessionBlock: suspend DefaultClientWebSocketSession.() -> Unit,
    private val onDisconnected: suspend (exception: Throwable) -> Unit = {},
    private val expectedFingerprint: String,
    private val coroutineScope: CoroutineScope,
) {
    private var connectionJob: Job? = null
    private var retryJob: Job? = null

    fun connect() {
        if (connectionJob?.isActive == true) {
            return
        }
        connectionJob =
            coroutineScope.launch {
                if (device is Device.RemoteServer) {
                    connectToRelay()
                } else {
                    connect(device.addresses)
                }
            }
    }

    private suspend fun connect(remainingHosts: Set<InetAddress>) {
        val host = remainingHosts.first()
        val nextHosts = remainingHosts - host
        doConnect(host, nextHosts)
    }

    private suspend fun doConnect(
        host: InetAddress,
        nextHosts: Set<InetAddress>,
    ) {
        var lastException: Exception? = null

        Logger.i(TAG) { "Connecting to $host for $endpointPath (device: $device)" }

        try {
            startWebSocket(host)
        } catch (
            @Suppress("TooGenericExceptionCaught") ex: Exception,
        ) {
            lastException = ex
        } finally {
            when (lastException) {
                is CancellationException,
                null,
                    -> {
                        Logger.i(TAG) { "Connection closed to $host for $endpointPath" }
                        onDisconnected(lastException ?: CancellationException("Closed by client"))
                    }

                is CertificateException -> {
                    Logger.w(TAG) { "Certificate mismatch for server $host for $endpointPath: ${lastException.message}" }
                    connectionJob = null
                    onDisconnected(lastException)
                }

                is PairingRevokedException -> {
                    Logger.w(TAG) { "Pairing revoked by server $host for $endpointPath: ${lastException.message}" }
                    connectionJob = null
                    onDisconnected(lastException)
                }

                is ClosedSendChannelException,
                is ClosedReceiveChannelException,
                is WebSocketException,
                is ProtocolException,
                    -> {
                        Logger.i(TAG) { "Connection closed by server $host for $endpointPath: ${lastException.prettify()}" }
                        onDisconnected(lastException)
                    }

                else -> {
                    Logger.w(TAG) { "Failed to connect to $host for $endpointPath: ${lastException.prettify()}" }
                    if (nextHosts.isNotEmpty()) {
                        delay(Constants.WEBSOCKET_CONNECTION_ATTEMPT_DELAY)
                        val nextHost = nextHosts.first()
                        doConnect(nextHost, nextHosts - nextHost)
                    } else {
                        Logger.w(TAG) { "Failed to connect to any of the hosts for $endpointPath" }
                        connectionJob = null
                        onDisconnected(ConnectException("Connection failure"))
                    }
                }
            }
        }
        connectionJob = null
    }

    private suspend fun startWebSocket(host: InetAddress) {
        val pinnedClient =
            HttpClient(CIO) {
                install(WebSockets) { clientPingInterval = Constants.WEBSOCKET_PING_PERIOD }
                engine {
                    https {
                        trustManager = PinnedTrustManager(expectedFingerprint)
                        serverName = Constants.TLS_SERVER_NAME
                    }
                }
            }
        pinnedClient.use {
            it.wss(
                method = HttpMethod.Get,
                host = host.hostAddress,
                port = Constants.SERVICE_PORT,
                path = endpointPath,
            ) {
                pingInterval = Constants.WEBSOCKET_PING_PERIOD
                timeout = Constants.WEBSOCKET_TIMEOUT

                runSession()
            }
        }
    }

    private suspend fun connectToRelay() {
        val getRelayConfiguration = KoinPlatform.getKoin().get<GetRelayConfigurationFlowUseCase>()
        val configuration = getRelayConfiguration().first()
        if (!configuration.isConfigured) {
            error("No relay configured - cannot connect to $device")
        }
        relayWss(
            configuration = configuration,
            role = AppRole.CLIENT,
            endpoint = relayEndpointFor(endpointPath),
            serverId = device.id,
        ) {
            runSession()
        }
    }

    /**
     * Runs [sessionBlock] and, if the session ends because the server closed it with a policy violation, translates the
     * close reason into a [PairingRevokedException] so the connection is dropped for good instead of retried.
     */
    private suspend fun DefaultClientWebSocketSession.runSession() {
        try {
            sessionBlock()
        } catch (
            @Suppress("TooGenericExceptionCaught") ex: Exception,
        ) {
            val reason = closeReason.await() ?: throw ex
            throw when (reason.knownReason) {
                CloseReason.Codes.VIOLATED_POLICY -> PairingRevokedException(reason.message)
                CloseReason.Codes.PROTOCOL_ERROR -> ProtocolException(reason.message)
                else -> ex
            }
        }
    }

    fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null
        retryJob?.cancel()
        retryJob = null
    }

    private companion object {
        private val TAG = WebSocketConnectionHandler::class

        private fun relayEndpointFor(endpointPath: String): String =
            when (endpointPath) {
                Endpoints.CONTROL -> Endpoints.Relay.CLIENT_CONTROL
                Endpoints.STREAM_AUDIO -> Endpoints.Relay.CLIENT_AUDIO
                Endpoints.STREAM_VIDEO -> Endpoints.Relay.CLIENT_VIDEO
                else -> error("Unknown endpoint: $endpointPath")
            }
    }
}

/** Thrown when the server closes a connection because this client is no longer paired with it. */
internal class PairingRevokedException(
    message: String,
) : Exception(message)
