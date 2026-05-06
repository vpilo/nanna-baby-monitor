package org.vpilo.babymonitor.network.client

import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSocketException
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.plugins.websocket.wss
import io.ktor.http.HttpMethod
import io.ktor.http.encodeURLPathPart
import io.ktor.websocket.pingInterval
import io.ktor.websocket.timeout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.common.ktx.prettify
import org.vpilo.babymonitor.network.common.Constants
import org.vpilo.babymonitor.network.common.RelayHandshake
import org.vpilo.babymonitor.network.common.Server
import org.vpilo.babymonitor.network.common.deriveSharedRelaySecret
import org.vpilo.babymonitor.network.common.relayHttpClient
import java.net.InetAddress
import java.net.ProtocolException
import kotlin.coroutines.cancellation.CancellationException

internal class WebSocketConnectionHandler(
    private val server: Server,
    private val endpointPath: String,
    private val sessionBlock: suspend DefaultClientWebSocketSession.() -> Unit,
    private val onDisconnected: suspend (exception: Throwable) -> Unit = {},
    private val coroutineScope: CoroutineScope,
) {
    private var connectionJob: Job? = null
    private var retryJob: Job? = null

    fun connect() {
        if (connectionJob?.isActive == true) {
            return
        }
        connect(server.addresses)
    }

    private fun connect(remainingHosts: Set<InetAddress>) {
        check(connectionJob == null)
        val host = remainingHosts.first()
        val nextHosts = remainingHosts - host
        connectionJob = coroutineScope.launch { doConnect(host, nextHosts) }
    }

    private suspend fun doConnect(
        host: InetAddress,
        nextHosts: Set<InetAddress>,
    ) {
        var lastException: Exception? = null

        Logger.i(TAG) { "Connecting to $host for $endpointPath (local: ${server.id.isLocalServer})" }

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
                    }
                }
            }
        }
        connectionJob = null
    }

    private suspend fun startWebSocket(host: InetAddress) {
        if (server.id.isLocalServer) {
            networkClient.webSocket(
                method = HttpMethod.Get,
                host = host.hostAddress,
                port = Constants.WEBSOCKET_PORT,
                path = endpointPath,
            ) {
                pingInterval = Constants.WEBSOCKET_PING_PERIOD
                timeout = Constants.WEBSOCKET_TIMEOUT

                sessionBlock()
            }
        } else {
            relayHttpClient.wss(
                method = HttpMethod.Get,
                host = host.hostName,
                port = Constants.RELAY_PORT,
                path = "/relay/client$endpointPath/${server.id.name.encodeURLPathPart()}",
            ) {
                pingInterval = Constants.WEBSOCKET_PING_PERIOD
                timeout = Constants.WEBSOCKET_TIMEOUT

                RelayHandshake.send(this, secret)
                sessionBlock()
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
        private val secret by lazy { deriveSharedRelaySecret() }
    }
}
