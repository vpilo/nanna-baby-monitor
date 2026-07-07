package org.vpilo.babymonitor.network.client

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.common.ktx.prettify
import org.vpilo.babymonitor.model.AppRole
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.model.repository.ConnectionState
import org.vpilo.babymonitor.model.repository.NetworkClientRepository
import org.vpilo.babymonitor.model.repository.PairingOutcome
import org.vpilo.babymonitor.model.repository.ServerState
import org.vpilo.babymonitor.network.client.pairing.ClientPairingConnector
import org.vpilo.babymonitor.network.client.websockets.controlClientWebSocket
import org.vpilo.babymonitor.network.common.Constants
import org.vpilo.babymonitor.network.common.Endpoints
import org.vpilo.babymonitor.network.common.ForegroundServiceLink
import org.vpilo.babymonitor.settings.model.repository.PairingRepository
import org.vpilo.babymonitor.settings.model.usecase.GetLocalClientDeviceFlowUseCase
import kotlin.coroutines.CoroutineContext

internal class DefaultNetworkClientRepository(
    private val serverSelectionDataSource: ServerSelectionDataSource,
    networkControlDataSource: NetworkControlDataSource,
    private val pairingConnector: ClientPairingConnector,
    private val pairingRepository: PairingRepository,
    private val getLocalClientDeviceFlowUseCase: GetLocalClientDeviceFlowUseCase,
    coroutineContext: CoroutineContext,
) : NetworkClientRepository {
    private val scope = CoroutineScope(SupervisorJob() + coroutineContext)

    private val connectionState: MutableStateFlow<ConnectionState> =
        MutableStateFlow(ConnectionState.Disconnected(ConnectionState.ErrorReason.NotConnectedYet))
    override val connectionStateFlow: Flow<ConnectionState> = connectionState.asStateFlow()

    override val serverStateFlow: Flow<ServerState> = networkControlDataSource.serverState

    private var controlHandler: WebSocketConnectionHandler? = null

    private val foregroundLink = ForegroundServiceLink(AppRole.CLIENT)

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
                pairingRepository = pairingRepository,
                coroutineScope = scope,
            ).apply { connect() }

        connectionState.value = ConnectionState.Connecting(server)
    }

    override suspend fun pairWith(
        server: Device.Server,
        pin: String,
    ): PairingOutcome {
        val clientDevice = getLocalClientDeviceFlowUseCase().first()
        return pairingConnector.pairWith(server, clientDevice, pin)
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

        if (connectionState.value is ConnectionState.Disconnected) {
            return
        }
        // Reconnection failure case
        if (serverSelectionDataSource.server.value == null) {
            connectionState.value = ConnectionState.Disconnected(ConnectionState.ErrorReason.ServerNotFound)
            Logger.i(TAG) { "Client state: ${connectionState.value}" }
            return
        }

        connectionState.value = ConnectionState.Reconnecting(server)
        Logger.i(TAG) { "Client state: ${connectionState.value}" }

        scope.launch {
            do {
                delay(Constants.RECONNECTION_TIMEOUT)
                if (serverSelectionDataSource.server.value == null) {
                    Logger.d(TAG) { "Stopped reconnecting" }
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
