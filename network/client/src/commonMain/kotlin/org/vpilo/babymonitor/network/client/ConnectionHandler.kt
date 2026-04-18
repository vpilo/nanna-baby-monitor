package org.vpilo.babymonitor.network.client

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.common.Logger
import java.net.InetAddress
import java.net.SocketException
import kotlin.coroutines.cancellation.CancellationException

internal class ConnectionHandler(
    private val hosts: Set<InetAddress>,
    private val connectLambda: suspend (InetAddress) -> Boolean,
    private val onDisconnected: suspend (exception: Throwable) -> Unit,
    private val coroutineScope: CoroutineScope,
) {
    private var connectionJob: Job? = null

    fun connect() {
        if (connectionJob?.isActive == true) {
            // Already connected, or connecting
            return
        }
        require(hosts.isNotEmpty()) { "No hosts left to connect to" }
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
                        connectLambda(host)
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
        private val TAG = ConnectionHandler::class
    }
}
