package org.vpilo.babymonitor.network.client

import io.ktor.client.plugins.websocket.wss
import io.ktor.http.HttpMethod
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.repository.ServerId
import org.vpilo.babymonitor.network.common.Constants
import org.vpilo.babymonitor.network.common.RelayHandshake
import org.vpilo.babymonitor.network.common.relayHttpClient
import kotlin.time.Duration.Companion.seconds

internal class RelayDiscoverySource(
    private val secret: ByteArray,
) {
    private val _serverIds = MutableStateFlow<Set<ServerId>>(emptySet())
    val serverIds: StateFlow<Set<ServerId>> = _serverIds.asStateFlow()

    private var relayHost: String = ""
    private var discoveryJob: Job? = null

    fun updateRelayHost(host: String, scope: CoroutineScope) {
        relayHost = host
        discoveryJob?.cancel()
        _serverIds.value = emptySet()
        if (host.isEmpty()) return
        discoveryJob = scope.launch { runDiscoveryLoop() }
    }

    private suspend fun runDiscoveryLoop() {
        while (true) {
            @Suppress("TooGenericExceptionCaught")
            try {
                relayHttpClient.wss(
                    method = HttpMethod.Get,
                    host = relayHost,
                    port = Constants.RELAY_PORT,
                    path = "/relay/discovery",
                ) {
                    RelayHandshake.send(this, secret)
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val ids = frame.readText()
                                .lines()
                                .filter { it.isNotEmpty() }
                                .map { ServerId(it) }
                                .toSet()
                            _serverIds.value = ids
                        }
                    }
                }
            } catch (e: Exception) {
                when (e) {
                    is CancellationException -> throw e
                    else -> {
                        Logger.w(TAG) { "Relay discovery disconnected: ${e.message}. Retrying in 5s." }
                        _serverIds.value = emptySet()
                        delay(5.seconds)
                    }
                }
            }
        }
    }

    private companion object {
        private val TAG = RelayDiscoverySource::class
    }
}
