package org.vpilo.babymonitor.network.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.plugins.websocket.wss
import io.ktor.http.HttpMethod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
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
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.CaptureMode
import org.vpilo.babymonitor.model.repository.NetworkClientRepository
import org.vpilo.babymonitor.model.repository.NetworkState
import org.vpilo.babymonitor.model.repository.ServerId
import org.vpilo.babymonitor.model.repository.ServerState
import org.vpilo.babymonitor.network.client.websockets.audioStreamingClientWebSocket
import org.vpilo.babymonitor.network.client.websockets.controlClientWebSocket
import org.vpilo.babymonitor.network.client.websockets.videoStreamingClientWebSocket
import org.vpilo.babymonitor.network.common.Constants
import org.vpilo.babymonitor.network.common.DiscoveredServer
import org.vpilo.babymonitor.network.common.DiscoveryManager
import org.vpilo.babymonitor.network.common.Endpoints
import org.vpilo.babymonitor.network.common.RelayHandshake
import org.vpilo.babymonitor.network.common.deriveSharedRelaySecret
import org.vpilo.babymonitor.network.common.relayHttpClient
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

    private val networkClient: HttpClient by lazy {
        HttpClient(CIO) {
            install(WebSockets)
        }
    }

    private val connectionState: MutableStateFlow<NetworkState> =
        MutableStateFlow(NetworkState.Disconnected(NetworkState.ErrorReason.NotConnectedYet))
    override val connectionStateFlow: Flow<NetworkState> = connectionState.asStateFlow()

    override val serverStateFlow: StateFlow<ServerState> = dataSource.serverState

    private val localServers = MutableStateFlow<Set<DiscoveredServer>>(emptySet())

    private val secret = deriveSharedRelaySecret()
    private val relayDiscoverySource = RelayDiscoverySource(secret)

    override val discoveredServerIdsFlow: Flow<Set<ServerId>> =
        combine(
            localServers.map { set -> set.map { it.id }.toSet() },
            relayDiscoverySource.serverIds,
        ) { localIds, relayIds ->
            val localNames = localIds.map { it.name }.toSet()
            localIds + relayIds.filter { it.name !in localNames }
        }

    private var relayHost: String = ""
    private var isRelayConnection: Boolean = false
    private var connectedServerId: ServerId? = null

    private var currentAddress: InetAddress? = null
    private var isAudioEnabled: Boolean = false
    private var isVideoEnabled: Boolean = true
    private var controlConnectionHandler: ConnectionHandler? = null
    private var audioConnectionHandler: ConnectionHandler? = null
    private var videoConnectionHandler: ConnectionHandler? = null
    private var relayControlHandler: RelayConnectionHandler? = null
    private var relayAudioHandler: RelayConnectionHandler? = null
    private var relayVideoHandler: RelayConnectionHandler? = null
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

        isRelayConnection = isRelay
        connectedServerId = server
        closeAllConnections()

        if (isRelay) {
            relayControlHandler =
                RelayConnectionHandler(
                    coroutineScope = scope,
                    connectLambda = {
                        var result = false
                        relayHttpClient.wss(
                            method = HttpMethod.Get,
                            host = relayHost,
                            port = Constants.RELAY_PORT,
                            path = "/relay${Endpoints.CONTROL}/${server.name}",
                        ) {
                            RelayHandshake.send(this, secret)
                            onControlConnectionOpened(server, InetAddress.getByName(relayHost))
                            result = controlClientWebSocket()
                        }
                        result
                    },
                    onDisconnected = { onControlConnectionClosed(it) },
                ).apply { connect() }
        } else {
            val addresses = checkNotNull(localServer).addresses
            controlConnectionHandler =
                ConnectionHandler(
                    coroutineScope = scope,
                    hosts = addresses,
                    connectLambda = { host ->
                        Logger.i(TAG) { "Connecting to server $server" }
                        var result = false
                        networkClient.webSocket(
                            method = HttpMethod.Get,
                            host = host.hostAddress,
                            port = Constants.WEBSOCKET_PORT,
                            path = Endpoints.CONTROL,
                        ) {
                            onControlConnectionOpened(server, host)
                            result = controlClientWebSocket()
                        }
                        result
                    },
                    onDisconnected = { onControlConnectionClosed(it) },
                ).apply { connect() }
        }

        connectionState.value = NetworkState.Connecting(server)
    }

    private fun startAudioStream() {
        if (currentAddress == null || serverStateFlow.value.captureMode == CaptureMode.VIDEO_ONLY) return
        if (audioConnectionHandler != null || relayAudioHandler != null) {
            Logger.w(TAG) { "Audio stream is already running" }
            return
        }
        if (isRelayConnection) startRelayAudioStream() else startLanAudioStream()
        Logger.d(TAG) { "Audio stream started" }
    }

    private fun startRelayAudioStream() {
        val serverId = checkNotNull(connectedServerId)
        relayAudioHandler =
            RelayConnectionHandler(
                coroutineScope = scope,
                connectLambda = {
                    var result = false
                    relayHttpClient.wss(
                        method = HttpMethod.Get,
                        host = relayHost,
                        port = Constants.RELAY_PORT,
                        path = "/relay${Endpoints.STREAM_AUDIO}/${serverId.name}",
                    ) {
                        RelayHandshake.send(this, secret)
                        dataSource.setIsStreamingAudio(true)
                        result = audioStreamingClientWebSocket()
                    }
                    result
                },
                onDisconnected = {
                    dataSource.setIsStreamingAudio(false)
                    Logger.i(TAG) { "Relay audio disconnected, reconnecting" }
                    delay(1.seconds)
                    relayAudioHandler?.connect()
                },
            ).apply { connect() }
    }

    private fun startLanAudioStream() {
        audioConnectionHandler =
            ConnectionHandler(
                coroutineScope = scope,
                hosts = setOf(checkNotNull(currentAddress)),
                connectLambda = { host ->
                    var result = false
                    networkClient.webSocket(
                        method = HttpMethod.Get,
                        host = host.hostAddress,
                        port = Constants.WEBSOCKET_PORT,
                        path = Endpoints.STREAM_AUDIO,
                    ) {
                        dataSource.setIsStreamingAudio(true)
                        result = audioStreamingClientWebSocket()
                    }
                    result
                },
                onDisconnected = {
                    dataSource.setIsStreamingAudio(false)
                    Logger.i(TAG) { "Audio disconnected, reconnecting" }
                    delay(1.seconds)
                    audioConnectionHandler?.connect()
                },
            ).apply { connect() }
    }

    private fun startVideoStream() {
        if (currentAddress == null || serverStateFlow.value.captureMode == CaptureMode.AUDIO_ONLY) return
        if (videoConnectionHandler != null || relayVideoHandler != null) {
            Logger.w(TAG) { "Video stream is already running" }
            return
        }
        if (isRelayConnection) startRelayVideoStream() else startLanVideoStream()
        Logger.d(TAG) { "Video stream started" }
    }

    private fun startRelayVideoStream() {
        val serverId = checkNotNull(connectedServerId)
        relayVideoHandler =
            RelayConnectionHandler(
                coroutineScope = scope,
                connectLambda = {
                    var result = false
                    relayHttpClient.wss(
                        method = HttpMethod.Get,
                        host = relayHost,
                        port = Constants.RELAY_PORT,
                        path = "/relay${Endpoints.STREAM_VIDEO}/${serverId.name}",
                    ) {
                        RelayHandshake.send(this, secret)
                        dataSource.setIsStreamingVideo(true)
                        result = videoStreamingClientWebSocket()
                    }
                    result
                },
                onDisconnected = {
                    dataSource.setIsStreamingVideo(false)
                    Logger.i(TAG) { "Relay video disconnected, reconnecting" }
                    delay(1.seconds)
                    relayVideoHandler?.connect()
                },
            ).apply { connect() }
    }

    private fun startLanVideoStream() {
        videoConnectionHandler =
            ConnectionHandler(
                coroutineScope = scope,
                hosts = setOf(checkNotNull(currentAddress)),
                connectLambda = { host ->
                    var result = false
                    networkClient.webSocket(
                        method = HttpMethod.Get,
                        host = host.hostAddress,
                        port = Constants.WEBSOCKET_PORT,
                        path = Endpoints.STREAM_VIDEO,
                    ) {
                        dataSource.setIsStreamingVideo(true)
                        result = videoStreamingClientWebSocket()
                    }
                    result
                },
                onDisconnected = {
                    dataSource.setIsStreamingVideo(false)
                    Logger.i(TAG) { "Video disconnected, reconnecting" }
                    delay(1.seconds)
                    videoConnectionHandler?.connect()
                },
            ).apply { connect() }
    }

    private fun stopAudioStream() {
        audioConnectionHandler?.disconnect()
        audioConnectionHandler = null
        relayAudioHandler?.disconnect()
        relayAudioHandler = null
    }

    private fun stopVideoStream() {
        videoConnectionHandler?.disconnect()
        videoConnectionHandler = null
        relayVideoHandler?.disconnect()
        relayVideoHandler = null
    }

    private fun closeAllConnections() {
        stopAudioStream()
        stopVideoStream()
        controlConnectionHandler?.disconnect()
        controlConnectionHandler = null
        relayControlHandler?.disconnect()
        relayControlHandler = null
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
