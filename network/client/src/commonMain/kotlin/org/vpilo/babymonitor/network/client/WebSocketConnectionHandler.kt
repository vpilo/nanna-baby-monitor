package org.vpilo.babymonitor.network.client

import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.plugins.websocket.wss
import io.ktor.http.HttpMethod
import io.ktor.http.encodeURLPathPart
import io.ktor.websocket.pingInterval
import io.ktor.websocket.timeout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
import java.net.SocketException
import kotlin.coroutines.cancellation.CancellationException

internal class WebSocketConnectionHandler(
    private val server: Server,
    private val endpointPath: String,
    private val sessionBlock: suspend DefaultClientWebSocketSession.() -> Boolean,
    private val onDisconnected: suspend (exception: Throwable) -> Unit = {},
    private val coroutineScope: CoroutineScope,
    private val reconnect: Boolean = false,
) {
    private var connectionJob: Job? = null
    private var isDisconnectionHandled = false

    fun connect() {
        if (connectionJob?.isActive == true) {
            return
        }
        connect(server.addresses)
    }

    private fun connect(remainingHosts: Set<InetAddress>) {
        val host = remainingHosts.first()
        Logger.i(TAG) { "Connecting with $host (local: ${server.id.isLocalServer})" }
        isDisconnectionHandled = false
        connectionJob =
            coroutineScope
                .launch {
                    var lastException: Exception? = null

                    val connectionSucceeded =
                        try {
                            startWebSocket(host)
                        } catch (
                            @Suppress("TooGenericExceptionCaught") ex: Exception,
                        ) {
                            when (ex) {
                                is CancellationException -> {
                                    Logger.i(TAG) { "Connection closed for $endpointPath" }
                                    isDisconnectionHandled = true
                                    onDisconnected(ex)
                                    throw ex
                                }

                                else -> {
                                    Logger.w(TAG) { "Failed to connect to $host for $endpointPath: ${ex.prettify()}" }
                                    isDisconnectionHandled = true
                                    lastException = ex
                                    false
                                }
                            }
                        }
                    if (!connectionSucceeded) {
                        val nextHosts = remainingHosts - host
                        if (nextHosts.isNotEmpty()) {
                            connect(nextHosts)
                        } else {
                            Logger.w(TAG) { "Failed to connect to any of the hosts for $endpointPath" }
                            isDisconnectionHandled = true
                            onDisconnected(lastException ?: SocketException("Failed to connect to any host"))
                        }
                    }
                    connectionJob = null
                }.apply {
                    invokeOnCompletion {
                        if (isDisconnectionHandled) return@invokeOnCompletion
                        Logger.w(TAG) { "Disconnection for $endpointPath" }
                        isDisconnectionHandled = true
                        coroutineScope.launch {
                            onDisconnected(it ?: CancellationException("Unhandled closure"))
                            if (reconnect) {
                                Logger.i(TAG) { "Attempting to reconnect for $endpointPath" }
                                delay(Constants.RECONNECTION_TIMEOUT)
                                connect()
                            }
                        }
                    }
                }
    }

    private suspend fun startWebSocket(host: InetAddress): Boolean {
        var result = false
        if (server.id.isLocalServer) {
            networkClient.webSocket(
                method = HttpMethod.Get,
                host = host.hostAddress,
                port = Constants.WEBSOCKET_PORT,
                path = endpointPath,
            ) {
                pingInterval = Constants.WEBSOCKET_PING_PERIOD
                timeout = Constants.WEBSOCKET_TIMEOUT

                result = sessionBlock()
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
                result = sessionBlock()
            }
        }
        return result
    }

    fun disconnect() {
        isDisconnectionHandled = true
        connectionJob?.cancel()
        connectionJob = null
    }

    private companion object {
        private val TAG = WebSocketConnectionHandler::class
        private val secret by lazy { deriveSharedRelaySecret() }
    }
}
