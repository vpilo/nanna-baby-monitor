package org.vpilo.babymonitor.network.client

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.common.Logger
import javax.net.ssl.SSLException
import kotlin.coroutines.cancellation.CancellationException

internal class RelayConnectionHandler(
    private val connectLambda: suspend () -> Boolean,
    private val onDisconnected: suspend (exception: Throwable) -> Unit,
    private val coroutineScope: CoroutineScope,
) {
    private var connectionJob: Job? = null

    fun connect() {
        if (connectionJob?.isActive == true) return
        connectionJob = coroutineScope.launch {
            @Suppress("TooGenericExceptionCaught")
            val succeeded = try {
                connectLambda()
            } catch (ex: Exception) {
                when (ex) {
                    is CancellationException -> {
                        onDisconnected(ex)
                        throw ex
                    }
                    else -> {
                        Logger.w(TAG) { "Relay connection failed: ${ex::class.simpleName} (${ex.message})" }
                        false
                    }
                }
            }
            if (!succeeded) {
                onDisconnected(SSLException("Relay connection failed"))
            }
            connectionJob = null
        }
    }

    fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null
    }

    private companion object {
        private val TAG = RelayConnectionHandler::class
    }
}
