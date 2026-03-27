package org.vpilo.babymonitor.network.client

import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

internal class ConnectionHandler(
    private val doConnect: suspend () -> Unit,
    private val onDisconnected: suspend (exception: Throwable) -> Unit,
) {
    private var connectionJob: Job? = null

    suspend fun connect() {
        if (connectionJob?.isActive == true) {
            // Already connected, or connecting
            return
        }

        connectionJob = coroutineScope {
            launch {
                @Suppress("TooGenericExceptionCaught")
                try {
                    doConnect()
                } catch (ex: Exception) {
                    disconnect()
                    onDisconnected(ex)
                }
            }
        }
    }

    suspend fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null
    }
}
