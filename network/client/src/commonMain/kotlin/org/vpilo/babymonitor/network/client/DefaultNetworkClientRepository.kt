package org.vpilo.babymonitor.network.client

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.common.ktx.prettify
import org.vpilo.babymonitor.model.repository.NetworkClientRepository
import org.vpilo.babymonitor.model.repository.NetworkState
import org.vpilo.babymonitor.model.repository.ServerId
import org.vpilo.babymonitor.model.repository.ServerState
import org.vpilo.babymonitor.model.repository.ktx.reactor
import org.vpilo.babymonitor.network.client.websockets.controlClientWebSocket
import org.vpilo.babymonitor.network.common.DiscoveryManager
import org.vpilo.babymonitor.network.common.Endpoints
import org.vpilo.babymonitor.network.common.Server
import java.net.ConnectException
import java.net.InetAddress
import java.net.SocketException
import javax.net.ssl.SSLException
import kotlin.coroutines.CoroutineContext

internal class DefaultNetworkClientRepository(
    private val discoveryManager: DiscoveryManager,
    private val relayDiscoveryDataSource: RelayDiscoveryDataSource,
    private val serverSelectionDataSource: ServerSelectionDataSource,
    networkControlDataSource: NetworkControlDataSource,
    coroutineContext: CoroutineContext,
) : NetworkClientRepository {
    private val scope = CoroutineScope(SupervisorJob() + coroutineContext)

    private val connectionState: MutableStateFlow<NetworkState> =
        MutableStateFlow(NetworkState.Disconnected(NetworkState.ErrorReason.NotConnectedYet))
    override val connectionStateFlow: Flow<NetworkState> = connectionState.asStateFlow()

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

    init {
        // Ensure discovery is active if any clients are too.
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
                        connectionState.value = NetworkState.Disconnected(NetworkState.ErrorReason.ServerNotFound)
                        return
                    }
            }

        closeAllConnections()

        Logger.i(TAG) { "Connecting to server ${serverId.name} (local: ${serverId.isLocalServer})" }
        controlHandler =
            WebSocketConnectionHandler(
                server = server,
                endpointPath = Endpoints.CONTROL,
                onDisconnected = { onControlConnectionClosed(it) },
                sessionBlock = {
                    onControlConnectionOpened(server)
                    controlClientWebSocket()
                },
                coroutineScope = scope,
            ).apply { connect() }

        connectionState.value = NetworkState.Connecting(serverId)
    }

    override suspend fun reconnect() {
        val last = serverSelectionDataSource.lastServerId
        if (last == null) {
            Logger.w(TAG) { "No server to reconnect to." }
            return
        }
        Logger.i(TAG) { "Reconnecting to ${last.name}" }
        connect(last)
    }

    private fun closeAllConnections() {
        serverSelectionDataSource.set(null)
        controlHandler?.disconnect()
        controlHandler = null
    }

    override suspend fun disconnect() {
        closeAllConnections()
        connectionState.value = NetworkState.Disconnected(NetworkState.ErrorReason.ClientQuit)
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
        connectionState.value = NetworkState.Connected(server.id)
        Logger.i(TAG) { "Client state: ${connectionState.value}" }
    }

    private fun onControlConnectionClosed(exception: Throwable) {
        closeAllConnections()

        connectionState.value =
            when (exception) {
                is ConnectException -> {
                    Logger.i(TAG) { "Connection refused." }
                    NetworkState.Disconnected(NetworkState.ErrorReason.ServerNotFound)
                }

                is ClosedReceiveChannelException,
                is CancellationException,
                    -> {
                        Logger.i(TAG) { "Connection closed by client." }
                        NetworkState.Disconnected(NetworkState.ErrorReason.ClientQuit)
                    }

                is SocketException, is SSLException -> {
                    Logger.i(TAG) { "Connection closed: ${exception.prettify()}" }
                    NetworkState.Disconnected(NetworkState.ErrorReason.ConnectionFailed, exception)
                }

                else -> {
                    Logger.w(TAG) { "WebSocket failed: ${exception.prettify()}" }
                    NetworkState.Disconnected(NetworkState.ErrorReason.ServerQuit, exception)
                }
            }
        Logger.i(TAG) { "Client state: ${connectionState.value}" }
    }

    private companion object {
        private val TAG = DefaultNetworkClientRepository::class
    }
}
