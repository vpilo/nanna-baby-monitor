package org.vpilo.babymonitor.network.client

import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.plugins.websocket.wss
import io.ktor.http.HttpMethod
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.repository.ServerId
import org.vpilo.babymonitor.network.common.Constants
import org.vpilo.babymonitor.network.common.RelayHandshake
import org.vpilo.babymonitor.network.common.deriveSharedRelaySecret
import org.vpilo.babymonitor.network.common.relayHttpClient
import java.net.InetAddress
import java.net.SocketException
import kotlin.coroutines.cancellation.CancellationException

internal class WebSocketConnectionHandler(
    private val hosts: Set<InetAddress>,
    private val endpointPath: String,
    private val serverId: ServerId,
    private val sessionBlock: suspend DefaultClientWebSocketSession.(InetAddress) -> Boolean,
    private val onDisconnected: suspend (exception: Throwable) -> Unit,
    private val coroutineScope: CoroutineScope,
) {
    private var connectionJob: Job? = null

    fun connect() {
        if (connectionJob?.isActive == true) {
            return
        }
        connect(hosts)
    }

    private fun connect(remainingHosts: Set<InetAddress>) {
        val host = remainingHosts.first()
        Logger.i(TAG) { "Connecting with $host" }
        connectionJob =
            coroutineScope.launch {
                var lastException: Exception? = null

                @Suppress("TooGenericExceptionCaught")
                val connectionSucceeded =
                    try {
                        var result = false
                        if (!serverId.isLocalServer) {
                            relayHttpClient.wss(
                                method = HttpMethod.Get,
                                host = host.hostName,
                                port = Constants.RELAY_PORT,
                                path = "/relay$endpointPath/${serverId.name}",
                            ) {
                                RelayHandshake.send(this, secret)
                                result = sessionBlock(host)
                            }
                        } else {
                            networkClient.webSocket(
                                method = HttpMethod.Get,
                                host = host.hostAddress,
                                port = Constants.WEBSOCKET_PORT,
                                path = endpointPath,
                            ) {
                                result = sessionBlock(host)
                            }
                        }
                        result
                    } catch (ex: Exception) {
                        when (ex) {
                            is CancellationException -> {
                                onDisconnected(ex)
                                throw ex
                            }

                            else -> {
                                Logger.w(TAG) { "Failed to connect to $host: ${ex::class.simpleName} (${ex.message})" }
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
                        Logger.w(TAG) { "Failed to connect to any of the hosts" }
                        onDisconnected(lastException ?: SocketException("Failed to connect to any host"))
                    }
                }
                connectionJob = null
            }
    }

    fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null
    }

    private companion object {
        private val TAG = WebSocketConnectionHandler::class
        private val secret by lazy { deriveSharedRelaySecret() }
    }
}
