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
import org.vpilo.babymonitor.model.repository.LocalClientDeviceRepository
import org.vpilo.babymonitor.network.client.websockets.controlClientWebSocket
import org.vpilo.babymonitor.network.internal.BackgroundServiceLink
import org.vpilo.babymonitor.network.internal.repository.InternalActiveSessionsRepository
import org.vpilo.babymonitor.network.model.Constants
import org.vpilo.babymonitor.network.model.Endpoints
import org.vpilo.babymonitor.network.model.ServerState
import org.vpilo.babymonitor.network.model.repository.NetworkClientRepository
import org.vpilo.babymonitor.network.model.repository.PairingStorageRepository
import org.vpilo.babymonitor.network.model.repository.RelayConfigurationRepository
import java.net.ProtocolException
import java.security.cert.CertificateException
import kotlin.coroutines.CoroutineContext

internal class DefaultNetworkClientRepository(
    private val serverSelectionDataSource: ServerSelectionDataSource,
    private val networkControlDataSource: NetworkControlDataSource,
    private val relayConfigurationRepository: RelayConfigurationRepository,
    private val pairingStorageRepository: PairingStorageRepository,
    private val activeSessionsRepository: InternalActiveSessionsRepository,
    private val localClientDeviceRepository: LocalClientDeviceRepository,
    coroutineContext: CoroutineContext,
) : NetworkClientRepository {
    private val scope = CoroutineScope(SupervisorJob() + coroutineContext)

    private val connectionState: MutableStateFlow<ConnectionState> =
        MutableStateFlow(ConnectionState.Disconnected(ConnectionState.ErrorReason.NotConnectedYet))
    override val connectionStateFlow: Flow<ConnectionState> = connectionState.asStateFlow()

    override val serverStateFlow: Flow<ServerState> = networkControlDataSource.serverState

    private var controlHandler: WebSocketConnectionHandler? = null

    private val foregroundLink =
        BackgroundServiceLink(AppRole.CLIENT) {
            closeAllConnections()
        }

    override suspend fun connect(server: Device.Server) {
        val currentState = connectionState.value
        if (currentState is ConnectionState.Connecting || currentState is ConnectionState.Connected) {
            return
        }

        foregroundLink.start()
        closeAllConnections()

        Logger.i(TAG) { "Connecting to $server" }
        val expectedFingerprint =
            pairingStorageRepository.findServer(server.id)?.certFingerprint
                ?: error("Not paired with $server - unable to connect")
        val localDevice = localClientDeviceRepository.localDevice.first()
        controlHandler =
            WebSocketConnectionHandler(
                device = server,
                endpointPath = Endpoints.CONTROL,
                onDisconnected = { onControlConnectionClosed(server, it) },
                sessionBlock = {
                    onControlConnectionOpened(server)
                    controlClientWebSocket(
                        localDevice = localDevice,
                        serverDeviceId = server.id,
                        dataSource = networkControlDataSource,
                        pairingStorageRepository = pairingStorageRepository,
                        activeSessionsRepository = activeSessionsRepository,
                    )
                },
                expectedFingerprint = expectedFingerprint,
                relayConfiguration = relayConfigurationRepository.relayConfiguration.first(),
                coroutineScope = scope,
            ).apply { connect() }

        connectionState.value = ConnectionState.Connecting(server)
    }

    private fun closeAllConnections() {
        serverSelectionDataSource.set(null)
        controlHandler?.disconnect()
        controlHandler = null
    }

    fun disconnect(closeReason: ConnectionState.ErrorReason) {
        closeAllConnections()
        connectionState.value = ConnectionState.Disconnected(closeReason)
        foregroundLink.stop()
        Logger.i(TAG) { "Client state: ${connectionState.value}" }
    }

    override suspend fun disconnect() = disconnect(ConnectionState.ErrorReason.ClientQuit)

    override fun reset() = disconnect(ConnectionState.ErrorReason.NotConnectedYet)

    private fun onControlConnectionOpened(server: Device.Server) {
        serverSelectionDataSource.set(server)
        connectionState.value = ConnectionState.Connected(server)
        Logger.i(TAG) { "Client state: ${connectionState.value}" }
    }

    private fun onControlConnectionClosed(
        server: Device.Server,
        exception: Throwable,
    ) {
        if (connectionState.value is ConnectionState.Disconnected) {
            return
        }

        Logger.i(TAG) { "Control connection closed: ${exception.prettify()}" }

        when (exception) {
            is PairingRevokedException -> {
                Logger.w(TAG) { "Server revoked our pairing; unpairing $server and giving up" }
                scope.launch { pairingStorageRepository.unpairServer(server.id) }
                disconnect(ConnectionState.ErrorReason.PairingRevoked)
                return
            }

            is CertificateException -> {
                Logger.w(TAG) { "Server certificate did not match with pairing; unpairing $server and giving up" }
                scope.launch { pairingStorageRepository.unpairServer(server.id) }
                disconnect(ConnectionState.ErrorReason.CertificateMismatch)
                return
            }

            is ProtocolException -> {
                Logger.w(TAG) { "Server reported a protocol issue. Incompatible version?" }
                disconnect(ConnectionState.ErrorReason.ServerQuit)
                return
            }
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
