package org.vpilo.babymonitor.network.client

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.CaptureMode
import org.vpilo.babymonitor.model.repository.NetworkClientRepository
import org.vpilo.babymonitor.model.repository.NetworkState
import org.vpilo.babymonitor.model.repository.ServerId
import org.vpilo.babymonitor.model.repository.ServerState
import org.vpilo.babymonitor.network.client.websockets.audioStreamingClientWebSocket
import org.vpilo.babymonitor.network.client.websockets.controlClientWebSocket
import org.vpilo.babymonitor.network.client.websockets.videoStreamingClientWebSocket
import org.vpilo.babymonitor.network.common.DiscoveredServer
import org.vpilo.babymonitor.network.common.DiscoveryManager
import org.vpilo.babymonitor.network.common.Endpoints
import java.net.ConnectException
import java.net.InetAddress
import java.net.SocketException
import javax.net.ssl.SSLException
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds

internal class DefaultNetworkClientRepository(
    private val discoveryManager: DiscoveryManager,
    private val dataSource: NetworkControlDataSource,
    coroutineContext: CoroutineContext,
) : NetworkClientRepository {
    private val scope = CoroutineScope(SupervisorJob() + coroutineContext)

    private val connectionState: MutableStateFlow<NetworkState> =
        MutableStateFlow(NetworkState.Disconnected(NetworkState.ErrorReason.NotConnectedYet))
    override val connectionStateFlow: Flow<NetworkState> = connectionState.asStateFlow()

    override val serverStateFlow: StateFlow<ServerState> = dataSource.serverState

    private val localServers = MutableStateFlow<Set<DiscoveredServer>>(emptySet())
    private val relayDiscoverySource = RelayDiscoverySource()

    override val discoveredServerIdsFlow: Flow<Set<ServerId>> =
        combine(
            localServers.map { set -> set.map { it.id }.toSet() },
            relayDiscoverySource.serverIds,
        ) { localIds, relayIds ->
            val localNames = localIds.map { it.name }.toSet()
            localIds + relayIds.filter { it.name !in localNames }
        }

    private var relayHost: String = ""
    private var connectedServerId: ServerId? = null

    private var currentAddress: InetAddress? = null
    private var isAudioEnabled: Boolean = false
    private var isVideoEnabled: Boolean = true
    private var controlHandler: WebSocketConnectionHandler? = null
    private var audioHandler: WebSocketConnectionHandler? = null
    private var videoHandler: WebSocketConnectionHandler? = null
    private var serverStateJob: Job? = null

    init {
        discoveryManager.discoveredServers
            .onEach { localServers.value = it }
            .launchIn(scope)
    }

    override suspend fun connect(server: ServerId) {
        val isRelay = !server.isLocalServer
        val localServer = if (!isRelay) localServers.value.firstOrNull { it.id.name == server.name } else null

        if (!isRelay && localServer == null) {
            Logger.w(TAG) { "Server ${server.name} not found in local servers." }
            connectionState.value = NetworkState.Disconnected(NetworkState.ErrorReason.ServerNotFound)
            return
        }

        connectedServerId = server
        closeAllConnections()

        val hosts =
            if (isRelay) setOf(
                withContext(Dispatchers.IO) {
                    InetAddress.getByName(relayHost)
                }
            )
            else checkNotNull(localServer).addresses

        controlHandler =
            WebSocketConnectionHandler(
                hosts = hosts,
                endpointPath = Endpoints.CONTROL,
                serverId = server,
                onDisconnected = { onControlConnectionClosed(it) },
                sessionBlock = { address ->
                    onControlConnectionOpened(server, address)
                    controlClientWebSocket()
                },
                coroutineScope = scope,
            ).apply { connect() }

        connectionState.value = NetworkState.Connecting(server)
    }

    private fun startAudioStream() {
        if (currentAddress == null || serverStateFlow.value.captureMode == CaptureMode.VIDEO_ONLY) return
        if (audioHandler != null) {
            Logger.w(TAG) { "Audio stream is already running" }
            return
        }
        val serverId = checkNotNull(connectedServerId)

        audioHandler =
            WebSocketConnectionHandler(
                hosts = setOf(checkNotNull(currentAddress)),
                endpointPath = Endpoints.STREAM_AUDIO,
                serverId = serverId,
                onDisconnected = {
                    dataSource.setIsStreamingAudio(false)
                    Logger.i(TAG) { "Audio disconnected, reconnecting" }
                    delay(1.seconds)
                    audioHandler?.connect()
                },
                sessionBlock = { _ ->
                    dataSource.setIsStreamingAudio(true)
                    audioStreamingClientWebSocket()
                },
                coroutineScope = scope,
            ).apply { connect() }

        Logger.d(TAG) { "Audio stream started" }
    }

    private fun startVideoStream() {
        if (currentAddress == null || serverStateFlow.value.captureMode == CaptureMode.AUDIO_ONLY) return
        if (videoHandler != null) {
            Logger.w(TAG) { "Video stream is already running" }
            return
        }
        val serverId = checkNotNull(connectedServerId)

        videoHandler =
            WebSocketConnectionHandler(
                hosts = setOf(checkNotNull(currentAddress)),
                endpointPath = Endpoints.STREAM_VIDEO,
                serverId = serverId,
                onDisconnected = {
                    dataSource.setIsStreamingVideo(false)
                    Logger.i(TAG) { "Video disconnected, reconnecting" }
                    delay(1.seconds)
                    videoHandler?.connect()
                },
                sessionBlock = { _ ->
                    dataSource.setIsStreamingVideo(true)
                    videoStreamingClientWebSocket()
                },
                coroutineScope = scope,
            ).apply { connect() }

        Logger.d(TAG) { "Video stream started" }
    }

    private fun stopAudioStream() {
        audioHandler?.disconnect()
        audioHandler = null
    }

    private fun stopVideoStream() {
        videoHandler?.disconnect()
        videoHandler = null
    }

    private fun closeAllConnections() {
        stopAudioStream()
        stopVideoStream()
        controlHandler?.disconnect()
        controlHandler = null
        serverStateJob?.cancel()
        serverStateJob = null
        currentAddress = null
    }

    override fun enableAudio(enable: Boolean) {
        Logger.i(TAG) { "enableAudio: $enable" }
        isAudioEnabled = enable
        if (currentAddress == null || serverStateFlow.value.isStreamingAudio == enable) return
        if (enable) startAudioStream() else stopAudioStream()
    }

    override fun enableVideo(enable: Boolean) {
        Logger.i(TAG) { "enableVideo: $enable" }
        isVideoEnabled = enable
        if (currentAddress == null || serverStateFlow.value.isStreamingVideo == enable) return
        if (enable) startVideoStream() else stopVideoStream()
    }

    override suspend fun disconnect() {
        closeAllConnections()
        connectionState.value = NetworkState.Disconnected(NetworkState.ErrorReason.ClientQuit)
        Logger.i(TAG) { "Client state: ${connectionState.value}" }
    }

    override fun setRelayHost(host: String) {
        relayHost = host
        relayDiscoverySource.updateRelayHost(host, scope)
    }

    override fun setDeviceName(name: String) {
        discoveryManager.setDeviceName(name)
    }

    private fun onControlConnectionOpened(
        server: ServerId,
        address: InetAddress,
    ) {
        currentAddress = address
        connectionState.value = NetworkState.Connected(server)
        Logger.i(TAG) { "Client state: ${connectionState.value}" }

        if (isAudioEnabled) startAudioStream()
        if (isVideoEnabled) startVideoStream()

        serverStateJob =
            scope.launch {
                dataSource.serverState.collect { serverState ->
                    if (!serverState.isAvailable) {
                        return@collect
                    }
                    Logger.i(TAG) { "Server changed capture mode: ${serverState.captureMode}" }
                    when (serverState.captureMode) {
                        CaptureMode.AUDIO_ONLY -> {
                            stopVideoStream()
                        }

                        CaptureMode.VIDEO_ONLY -> {
                            stopAudioStream()
                        }

                        CaptureMode.AUDIO_AND_VIDEO -> {
                            // Do nothing
                        }
                    }
                }
            }
    }

    private fun onControlConnectionClosed(exception: Throwable) {
        closeAllConnections()

        connectionState.value =
            when (exception) {
                is ClosedReceiveChannelException -> {
                    return
                }

                is ConnectException -> {
                    Logger.i(TAG) { "Connection refused." }
                    NetworkState.Disconnected(NetworkState.ErrorReason.ServerNotFound)
                }

                is CancellationException -> {
                    Logger.i(TAG) { "Connection closed by client." }
                    NetworkState.Disconnected(NetworkState.ErrorReason.ClientQuit)
                }

                is SocketException, is SSLException -> {
                    Logger.i(TAG) { "Connection closed: ${exception.message}" }
                    NetworkState.Disconnected(NetworkState.ErrorReason.ConnectionFailed, exception)
                }

                else -> {
                    Logger.w(TAG) { "WebSocket failed: ${exception::class.simpleName} - ${exception.localizedMessage}" }
                    NetworkState.Disconnected(NetworkState.ErrorReason.ServerQuit, exception)
                }
            }
        Logger.i(TAG) { "Client state: ${connectionState.value}" }
    }

    private companion object {
        private val TAG = DefaultNetworkClientRepository::class
    }
}
