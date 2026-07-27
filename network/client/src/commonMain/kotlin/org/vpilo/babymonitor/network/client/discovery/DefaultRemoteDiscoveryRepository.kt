package org.vpilo.babymonitor.network.client.discovery

import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.pingInterval
import io.ktor.websocket.readText
import io.ktor.websocket.timeout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.common.ktx.prettify
import org.vpilo.babymonitor.model.AppRole
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.network.internal.protocol.runWebSocketCatching
import org.vpilo.babymonitor.network.model.Constants
import org.vpilo.babymonitor.network.model.Endpoints
import org.vpilo.babymonitor.network.model.RelayConfiguration
import org.vpilo.babymonitor.network.model.repository.RemoteDiscoveryRepository
import org.vpilo.babymonitor.network.model.transport.fromTransportString
import org.vpilo.babymonitor.network.security.relay.relayWss
import kotlin.coroutines.CoroutineContext

internal class DefaultRemoteDiscoveryRepository(
    coroutineContext: CoroutineContext,
) : RemoteDiscoveryRepository {
    private val scope = CoroutineScope(coroutineContext + SupervisorJob())

    private val _discoveredDevicesFlow = MutableStateFlow<Set<Device>>(emptySet())
    override val discoveredDevicesFlow: StateFlow<Set<Device>> = _discoveredDevicesFlow.asStateFlow()

    private val mutableIsRegisteredFlow: MutableStateFlow<Boolean> = MutableStateFlow(false)
    override val isRegisteredFlow: Flow<Boolean> = mutableIsRegisteredFlow.asStateFlow()

    private var relayConfiguration: RelayConfiguration = RelayConfiguration.NONE
    private var discoveryJob: Job? = null
    private var session: DefaultWebSocketSession? = null

    override fun setRelay(configuration: RelayConfiguration) {
        relayConfiguration = configuration
        _discoveredDevicesFlow.value = emptySet()

        discoveryJob?.cancel()
        discoveryJob = null

        if (configuration.isConfigured) {
            start()
        } else {
            scope.launch {
                session?.close()
                session = null
            }
        }
    }

    private fun start() {
        if (!relayConfiguration.isConfigured) return

        discoveryJob =
            scope.launch {
                session?.close()
                session = null
                mutableIsRegisteredFlow.value = false
                runDiscoveryLoop()
            }
    }

    private suspend fun runDiscoveryLoop() {
        while (true) {
            Logger.d(TAG) { "Relay discovery connection started for ${relayConfiguration.host}" }
            runCatching {
                relayWss(
                    configuration = relayConfiguration,
                    role = AppRole.CLIENT,
                    endpoint = Endpoints.Relay.CLIENT_DISCOVERY,
                ) {
                    pingInterval = Constants.WEBSOCKET_PING_PERIOD
                    timeout = Constants.WEBSOCKET_TIMEOUT

                    session = this
                    mutableIsRegisteredFlow.value = true
                    runWebSocketCatching(TAG) {
                        for (frame in incoming) {
                            if (frame !is Frame.Text) continue
                            val servers =
                                frame
                                    .readText()
                                    .lines()
                                    .filter { it.isNotEmpty() }
                                    .mapNotNull { line -> Device.RemoteServer.fromTransportString(line) }
                                    .filter { it.relayHost == relayConfiguration.host }
                                    .toSet()
                            if (_discoveredDevicesFlow.value != servers) {
                                Logger.i(TAG) { "Relay found servers: $servers" }
                            }
                            _discoveredDevicesFlow.value = servers
                        }
                    }
                }
            }.onFailure { ex ->
                if (ex is CancellationException) throw ex
                Logger.i(TAG) { "Relay discovery connection failed: ${ex.prettify()}. Retrying." }
            }
            mutableIsRegisteredFlow.value = false
            _discoveredDevicesFlow.value = emptySet()
            delay(Constants.RECONNECTION_TIMEOUT)
        }
    }

    private companion object {
        private val TAG = DefaultRemoteDiscoveryRepository::class
    }
}
