package org.vpilo.babymonitor.network.client.discovery

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
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.model.repository.RemoteDiscoveryRepository
import org.vpilo.babymonitor.network.common.Constants
import org.vpilo.babymonitor.network.common.RelayHandshake
import org.vpilo.babymonitor.network.common.deriveSharedRelaySecret
import org.vpilo.babymonitor.network.common.relayHttpClient
import kotlin.coroutines.CoroutineContext

internal class DefaultRemoteDiscoveryRepository(
    coroutineContext: CoroutineContext,
) : RemoteDiscoveryRepository {
    private val scope = CoroutineScope(coroutineContext + SupervisorJob())

    private val _discoveredDevicesFlow = MutableStateFlow<Set<Device>>(emptySet())
    override val discoveredDevicesFlow: StateFlow<Set<Device>> = _discoveredDevicesFlow.asStateFlow()

    private var relayHost: String = ""
    private var discoveryJob: Job? = null

    private var isEnabled: Boolean = true

    override fun setEnabled(host: String, enabled: Boolean) {
        isEnabled = enabled
        relayHost = host
        discoveryJob?.cancel()
        discoveryJob = null
        if (!enabled) {
            _discoveredDevicesFlow.value = emptySet()
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
                                    .mapNotNull { line -> line.toRemoteServer() }
                                    .toSet()
                            Logger.i(TAG) { "Relay found servers: $ids" }
                            _discoveredDevicesFlow.value = ids
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
                        _discoveredDevicesFlow.value = emptySet()
                        delay(Constants.RECONNECTION_TIMEOUT)
                    }
                }
            }
        }
    }

    private fun String.toRemoteServer(): Device.RemoteServer? {
        if (isBlank()) return null
        val (rawId, name) = split("#")
        val id = DeviceId.parseOrNull(rawId) ?: return null

        return Device.RemoteServer(
            id = id,
            name = name,
            relayHost = this@DefaultRemoteDiscoveryRepository.relayHost,
        )
    }

    private companion object {
        private val TAG = DefaultRemoteDiscoveryRepository::class
        private val secret by lazy { deriveSharedRelaySecret() }
    }
}
