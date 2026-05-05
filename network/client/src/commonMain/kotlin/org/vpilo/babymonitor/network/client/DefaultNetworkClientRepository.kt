package org.vpilo.babymonitor.network.client

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.common.ktx.prettify
import org.vpilo.babymonitor.model.AppRole
import org.vpilo.babymonitor.model.repository.ConnectionState
import org.vpilo.babymonitor.model.repository.NetworkClientRepository
import org.vpilo.babymonitor.model.repository.ServerId
import org.vpilo.babymonitor.model.repository.ServerState
import org.vpilo.babymonitor.model.repository.ktx.reactor
import org.vpilo.babymonitor.network.client.websockets.controlClientWebSocket
import org.vpilo.babymonitor.network.common.DiscoveryManager
import org.vpilo.babymonitor.network.common.Endpoints
import org.vpilo.babymonitor.network.common.ForegroundServiceLink
import org.vpilo.babymonitor.network.common.Server
import java.net.InetAddress
import kotlin.coroutines.CoroutineContext

internal class DefaultNetworkClientRepository(
    private val discoveryManager: DiscoveryManager,
    private val relayDiscoveryDataSource: RelayDiscoveryDataSource,
    private val serverSelectionDataSource: ServerSelectionDataSource,
    networkControlDataSource: NetworkControlDataSource,
    coroutineContext: CoroutineContext,
) : NetworkClientRepository {
    private val scope = CoroutineScope(SupervisorJob() + coroutineContext)

    private val connectionState: MutableStateFlow<ConnectionState> =
        MutableStateFlow(ConnectionState.Disconnected(ConnectionState.ErrorReason.NotConnectedYet))
    override val connectionStateFlow: Flow<ConnectionState> = connectionState.asStateFlow()

    override val serverStateFlow: Flow<ServerState> = networkControlDataSource.serverState

    override val discoveredServerIdsFlow: Flow<Set<ServerId>> =
        combine(
            discoveryManager.discoveredServersFlow.map { set -> set.map { it.id }.toSet() },
            relayDiscoveryDataSource.serverIds,
        ) { localIds, relayIds ->
            val localNames = localIds.map { it.name }.toSet()
            localIds + relayIds.filter { it.name !in localNames }
        }

    private var relayHost: String = ""

    private var controlHandler: WebSocketConnectionHandler? = null

    private val foregroundLink = ForegroundServiceLink(AppRole.CLIENT)

    init {
        // Ensure discovery is active if anyone is using this repository.
        connectionState.reactor(
            scope = scope,
            onActive = { discoveryManager.startDiscovery() },
            onInactive = { discoveryManager.stopDiscovery() },
        )
    }

    override suspend fun connect(serverId: ServerId) {
        val server =
            if (!serverId.isLocalServer) {
                Server(serverId, InetAddress.getByAddress(relayHost, ByteArray(4)))
            } else {
                discoveryManager.getDiscoveredServers().firstOrNull { it.id.name == serverId.name }
                    ?: run {
                        Logger.w(TAG) { "Server ${serverId.name} not found in local servers." }
                        connectionState.value = ConnectionState.Disconnected(ConnectionState.ErrorReason.ServerNotFound)
                        return
                    }
            }

        foregroundLink.start()
        closeAllConnections()

        Logger.i(TAG) { "Connecting to server ${serverId.name} (local: ${serverId.isLocalServer})" }
        controlHandler =
            WebSocketConnectionHandler(
                server = server,
                endpointPath = Endpoints.CONTROL,
                onDisconnected = { onControlConnectionClosed(serverId, it) },
                sessionBlock = {
                    onControlConnectionOpened(server)
                    controlClientWebSocket()
                },
                coroutineScope = scope,
                reconnect = true,
            ).apply { connect() }

        connectionState.value = ConnectionState.Connecting(serverId)
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

    override fun setRelayHost(host: String) {
        relayHost = host
        relayDiscoveryDataSource.updateRelayHost(host, scope)
    }

    override fun setDeviceName(name: String) {
        discoveryManager.setDeviceName(name)
    }

    private fun onControlConnectionOpened(server: Server) {
        serverSelectionDataSource.set(server)
        connectionState.value = ConnectionState.Connected(server.id)
        Logger.i(TAG) { "Client state: ${connectionState.value}" }
    }

    private fun onControlConnectionClosed(
        serverId: ServerId,
        exception: Throwable,
    ) {
        Logger.i(TAG) { "Control connection closed: ${exception.prettify()}" }
        connectionState.value = ConnectionState.Reconnecting(serverId)
        Logger.i(TAG) { "Client state: ${connectionState.value}" }
    }

    private companion object {
        private val TAG = DefaultNetworkClientRepository::class
    }
}
