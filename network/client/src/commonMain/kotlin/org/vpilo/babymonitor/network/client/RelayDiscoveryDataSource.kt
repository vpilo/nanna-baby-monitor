package org.vpilo.babymonitor.network.client

import io.ktor.client.plugins.websocket.wss
import io.ktor.http.HttpMethod
import io.ktor.websocket.Frame
import io.ktor.websocket.pingInterval
import io.ktor.websocket.readText
import io.ktor.websocket.timeout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.common.ktx.prettify
import org.vpilo.babymonitor.model.repository.ServerId
import org.vpilo.babymonitor.network.common.Constants
import org.vpilo.babymonitor.network.common.RelayHandshake
import org.vpilo.babymonitor.network.common.deriveSharedRelaySecret
import org.vpilo.babymonitor.network.common.relayHttpClient
import kotlin.coroutines.CoroutineContext

internal class RelayDiscoveryDataSource(
    coroutineContext: CoroutineContext,
) {
    private val scope = CoroutineScope(coroutineContext + SupervisorJob())

    private val _serverIds = MutableStateFlow<Set<ServerId>>(emptySet())
    val serverIds: StateFlow<Set<ServerId>> = _serverIds.asStateFlow()

    private var relayHost: String = ""
    private var discoveryJob: Job? = null

    private var isEnabled: Boolean = true

    fun updateRelayHost(host: String) {
        if (relayHost == host) return
        relayHost = host
        discoveryJob?.cancel()
        discoveryJob = null
        _serverIds.value = emptySet()
        start()
    }

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        discoveryJob?.cancel()
        discoveryJob = null
        if (!enabled) {
            _serverIds.value = emptySet()
        } else {
            start()
        }
    }

    private fun start() {
        if (!isEnabled) return
        if (relayHost.isEmpty()) return
        discoveryJob = scope.launch { runDiscoveryLoop() }
    }

    private suspend fun runDiscoveryLoop() {
        while (true) {
            try {
                Logger.d(TAG) { "Relay discovery connection started for $relayHost" }
                relayHttpClient.wss(
                    method = HttpMethod.Get,
                    host = relayHost,
                    port = Constants.RELAY_PORT,
                    path = "/relay/discovery",
                ) {
                    pingInterval = Constants.WEBSOCKET_PING_PERIOD
                    timeout = Constants.WEBSOCKET_TIMEOUT

                    RelayHandshake.send(this, secret)
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val ids =
                                frame
                                    .readText()
                                    .lines()
                                    .filter { it.isNotEmpty() }
                                    .map { ServerId(it, isLocalServer = false) }
                                    .toSet()
                            Logger.i(TAG) { "Relay found servers: $ids" }
                            _serverIds.value = ids
                        }
                    }
                }
            } catch (
                @Suppress("TooGenericExceptionCaught") ex: Exception,
            ) {
                when (ex) {
                    is CancellationException -> {
                        Logger.d(TAG) { "Relay discovery connection closed." }
                        throw ex
                    }

                    else -> {
                        Logger.w(TAG) { "Relay discovery disconnected: ${ex.prettify()}. Retrying." }
                        _serverIds.value = emptySet()
                        delay(Constants.RECONNECTION_TIMEOUT)
                    }
                }
            }
        }
    }

    private companion object {
        private val TAG = RelayDiscoveryDataSource::class
        private val secret by lazy { deriveSharedRelaySecret() }
    }
}
