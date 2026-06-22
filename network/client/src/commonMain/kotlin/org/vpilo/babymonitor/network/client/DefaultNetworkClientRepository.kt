package org.vpilo.babymonitor.network.client

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.common.ktx.prettify
import org.vpilo.babymonitor.model.AppRole
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.model.repository.ConnectionState
import org.vpilo.babymonitor.model.repository.DeviceStateRepository
import org.vpilo.babymonitor.model.repository.NetworkClientRepository
import org.vpilo.babymonitor.model.repository.ServerState
import org.vpilo.babymonitor.model.repository.ktx.reactor
import org.vpilo.babymonitor.network.client.websockets.controlClientWebSocket
import org.vpilo.babymonitor.network.common.Constants
import org.vpilo.babymonitor.network.common.Endpoints
import org.vpilo.babymonitor.network.common.ForegroundServiceLink
import org.vpilo.babymonitor.network.common.discovery.DiscoveryManager
import kotlin.coroutines.CoroutineContext

internal class DefaultNetworkClientRepository(
    private val discoveryManager: DiscoveryManager,
    private val relayDiscoveryDataSource: RelayDiscoveryDataSource,
    private val serverSelectionDataSource: ServerSelectionDataSource,
    networkControlDataSource: NetworkControlDataSource,
    deviceStateRepository: DeviceStateRepository,
    coroutineContext: CoroutineContext,
) : NetworkClientRepository {
    private val scope = CoroutineScope(SupervisorJob() + coroutineContext)

    private val connectionState: MutableStateFlow<ConnectionState> =
        MutableStateFlow(ConnectionState.Disconnected(ConnectionState.ErrorReason.NotConnectedYet))
    override val connectionStateFlow: Flow<ConnectionState> = connectionState.asStateFlow()

    override val serverStateFlow: Flow<ServerState> = networkControlDataSource.serverState

    override val discoveredDevicesFlow: Flow<Set<Device>> =
        combine(
            discoveryManager.discoveredDevicesFlow,
            relayDiscoveryDataSource.devices,
        ) { localDevices, relayDevices ->
            localDevices + relayDevices
        }

    private var relayHost: String = ""

    private var controlHandler: WebSocketConnectionHandler? = null

    private val foregroundLink = ForegroundServiceLink(AppRole.CLIENT)

    private lateinit var localDevice: Device.Client

    private var discoveryJob: Job? = null

    init {
        // When internet connectivity changes, re-enable discovery to ensure the server list is up to date.
        deviceStateRepository.isInternetAvailable
            .onEach {
                discoveryManager.refresh()
                relayDiscoveryDataSource.setEnabled(it)
            }.launchIn(scope)
    }

    override fun identifySelf(device: Device.Client) {
        localDevice = device
        // Ensure discovery is active if anyone is using this repository.
        discoveryJob?.cancel()
        discoveryJob =
            connectionState.reactor(
                scope = scope,
                onActive = { discoveryManager.register(localDevice) },
                onInactive = { discoveryManager.unregister() },
            )
    }

    override suspend fun connect(server: Device.Server) {
        val currentState = connectionState.value
        if (currentState is ConnectionState.Connecting || currentState is ConnectionState.Connected) {
            return
        }

        foregroundLink.start()
        closeAllConnections()

        Logger.i(TAG) { "Connecting to $server" }
        controlHandler =
            WebSocketConnectionHandler(
                device = server,
                endpointPath = Endpoints.CONTROL,
                onDisconnected = { onControlConnectionClosed(server, it) },
                sessionBlock = {
                    onControlConnectionOpened(server)
                    controlClientWebSocket()
                },
                coroutineScope = scope,
            ).apply { connect() }

        connectionState.value = ConnectionState.Connecting(server)
    }

    private fun closeAllConnections() {
        serverSelectionDataSource.set(null)
        controlHandler?.disconnect()
        controlHandler = null
    }

    override suspend fun disconnect() {
        closeAllConnections()
        connectionState.value = ConnectionState.Disconnected(ConnectionState.ErrorReason.ClientQuit)
        foregroundLink.stop()
        Logger.i(TAG) { "Client state: ${connectionState.value}" }
    }

    override fun reset() {
        closeAllConnections()
        foregroundLink.stop()
        connectionState.value = ConnectionState.Disconnected(ConnectionState.ErrorReason.NotConnectedYet)
    }

    override fun setRelayHost(host: String) {
        relayHost = host
        relayDiscoveryDataSource.updateRelayHost(host)
    }

    private fun onControlConnectionOpened(server: Device.Server) {
        serverSelectionDataSource.set(server)
        connectionState.value = ConnectionState.Connected(server)
        Logger.i(TAG) { "Client state: ${connectionState.value}" }
    }

    private fun onControlConnectionClosed(
        server: Device.Server,
        exception: Throwable,
    ) {
        Logger.i(TAG) { "Control connection closed: ${exception.prettify()}" }

        if (serverSelectionDataSource.server.value == null) return

        connectionState.value = ConnectionState.Reconnecting(server)
        Logger.i(TAG) { "Client state: ${connectionState.value}" }

        scope.launch {
            do {
                delay(Constants.RECONNECTION_TIMEOUT)
                if (serverSelectionDataSource.server.value == null) {
                    return@launch
                }
                connect(server)
            } while (connectionState.value is ConnectionState.Reconnecting)
        }
    }

    private companion object {
        private val TAG = DefaultNetworkClientRepository::class
    }
}
