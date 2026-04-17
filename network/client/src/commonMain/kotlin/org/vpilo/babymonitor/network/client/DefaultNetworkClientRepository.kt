package org.vpilo.babymonitor.network.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
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
import kotlinx.coroutines.flow.first
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
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.repository.SettingsRepository
import org.vpilo.babymonitor.settings.model.settings.DeviceName
import java.net.ConnectException
import java.net.InetAddress
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds

internal class DefaultNetworkClientRepository(
    discoveryManager: DiscoveryManager,
    private val dataSource: NetworkControlDataSource,
    private val settingsRepository: SettingsRepository,
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

    override val discoveredServerIdsFlow: Flow<Set<ServerId>> =
        discoveryManager.discoveredServers
            .onEach {
                Logger.d(TAG) { "Discovered servers updated: ${it.joinToString()}" }
            }.map { server -> server.map { it.id }.toSet() }

    private val discoveredServersFlow: Flow<Set<DiscoveredServer>> =
        discoveryManager.discoveredServers

    private var currentAddress: InetAddress? = null
    private var controlConnectionHandler: ConnectionHandler? = null
    private var audioConnectionHandler: ConnectionHandler? = null
    private var videoConnectionHandler: ConnectionHandler? = null
    private var serverStateJob: Job? = null

    init {
        settingsRepository
            .flowOf(Setting.DeviceName)
            .onEach { discoveryManager.setDeviceName(it) }
            .launchIn(scope)
    }

    override suspend fun connect(server: ServerId) {
        val addresses =
            discoveredServersFlow
                .first()
                .firstOrNull { it.id == server }
                ?.addresses
                ?: run {
                    Logger.w(TAG) { "Server with id ${server.name} not found among discovered servers." }
                    connectionState.value = NetworkState.Disconnected(NetworkState.ErrorReason.ServerNotFound)
                    return
                }
        val host = addresses.first()

        controlConnectionHandler?.disconnect()
        controlConnectionHandler =
            ConnectionHandler(
                coroutineScope = scope,
                connectLambda = {
                    networkClient.webSocket(
                        method = HttpMethod.Get,
                        host = host.hostAddress,
                        port = Constants.WEBSOCKET_PORT,
                        path = Endpoints.CONTROL,
                    ) {
                        onControlConnectionOpened(server, host)
                        controlClientWebSocket()
                    }
                },
                onDisconnected = { ex -> onControlConnectionClosed(ex) },
            ).apply { connect() }

        connectionState.value = NetworkState.Connecting(server)
        Logger.d(TAG) { "Connecting to server $server at $host..." }
    }

    private fun startAudioStream() {
        if (serverStateFlow.value.captureMode == CaptureMode.VIDEO_ONLY) {
            return
        }
        if (audioConnectionHandler != null) {
            Logger.w(TAG) { "Audio stream is already running" }
            return
        }
        audioConnectionHandler =
            ConnectionHandler(
                coroutineScope = scope,
                connectLambda = {
                    networkClient.webSocket(
                        method = HttpMethod.Get,
                        host = checkNotNull(currentAddress).hostAddress,
                        port = Constants.WEBSOCKET_PORT,
                        path = Endpoints.STREAM_AUDIO,
                    ) {
                        dataSource.setIsStreamingAudio(true)
                        audioStreamingClientWebSocket()
                    }
                },
                onDisconnected = {
                    dataSource.setIsStreamingAudio(false)
                    Logger.i(TAG) { "Audio disconnected, reconnecting" }
                    delay(1.seconds)
                    audioConnectionHandler?.connect()
                },
            ).apply { connect() }
        Logger.d(TAG) { "Audio stream started" }
    }

    private fun startVideoStream() {
        if (serverStateFlow.value.captureMode == CaptureMode.AUDIO_ONLY) {
            return
        }
        if (videoConnectionHandler != null) {
            Logger.w(TAG) { "Video stream is already running" }
            return
        }
        videoConnectionHandler =
            ConnectionHandler(
                coroutineScope = scope,
                connectLambda = {
                    networkClient.webSocket(
                        method = HttpMethod.Get,
                        host = checkNotNull(currentAddress).hostAddress,
                        port = Constants.WEBSOCKET_PORT,
                        path = Endpoints.STREAM_VIDEO,
                    ) {
                        dataSource.setIsStreamingVideo(true)
                        videoStreamingClientWebSocket()
                    }
                },
                onDisconnected = {
                    dataSource.setIsStreamingVideo(false)
                    Logger.i(TAG) { "Video disconnected, reconnecting" }
                    delay(1.seconds)
                    videoConnectionHandler?.connect()
                },
            ).apply { connect() }
        Logger.d(TAG) { "Video stream started" }
    }

    private fun stopAudioStream() {
        audioConnectionHandler?.disconnect()
        audioConnectionHandler = null
    }

    private fun stopVideoStream() {
        videoConnectionHandler?.disconnect()
        videoConnectionHandler = null
    }

    private fun closeAllConnections() {
        stopAudioStream()
        stopVideoStream()
        controlConnectionHandler?.disconnect()
        controlConnectionHandler = null
        serverStateJob?.cancel()
        serverStateJob = null
        currentAddress = null
    }

    override fun enableAudio(enable: Boolean) {
        Logger.i(TAG) { "enableAudio: $enable" }
        if (serverStateFlow.value.isStreamingAudio == enable) {
            return
        }
        if (audioConnectionHandler != null) {
            stopAudioStream()
        } else {
            startAudioStream()
        }
    }

    override fun enableVideo(enable: Boolean) {
        Logger.i(TAG) { "enableVideo: $enable" }
        if (serverStateFlow.value.isStreamingVideo == enable) {
            return
        }
        if (videoConnectionHandler != null) {
            stopVideoStream()
        } else {
            startVideoStream()
        }
    }

    override suspend fun disconnect() {
        closeAllConnections()

        connectionState.value = NetworkState.Disconnected(NetworkState.ErrorReason.ClientQuit)
        Logger.i(TAG) { "Client state: ${connectionState.value}" }
    }

    private fun onControlConnectionOpened(
        server: ServerId,
        address: InetAddress,
    ) {
        currentAddress = address
        connectionState.value = NetworkState.Connected(server)
        Logger.i(TAG) { "Client state: ${connectionState.value}" }

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
