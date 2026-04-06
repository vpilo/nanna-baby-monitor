package org.vpilo.babymonitor.network.client

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class ConnectionHandler(
    private val connectLambda: suspend () -> Unit,
    private val onDisconnected: suspend (exception: Throwable) -> Unit,
    private val coroutineScope: CoroutineScope,
) {
    private var connectionJob: Job? = null

    fun connect() {
        if (connectionJob?.isActive == true) {
            // Already connected, or connecting
            return
        }

        connectionJob =
            coroutineScope.launch {
                @Suppress("TooGenericExceptionCaught")
                try {
                    connectLambda()
                } catch (ex: Exception) {
                    disconnect()
                    onDisconnected(ex)
                }
            }
    }

    fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null
    }
}
