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
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.CaptureMode
import org.vpilo.babymonitor.model.repository.NetworkClientRepository
import org.vpilo.babymonitor.model.repository.NetworkState
import org.vpilo.babymonitor.model.repository.ServerState
import org.vpilo.babymonitor.network.client.websockets.audioStreamingClientWebSocket
import org.vpilo.babymonitor.network.client.websockets.controlClientWebSocket
import org.vpilo.babymonitor.network.client.websockets.videoStreamingClientWebSocket
import org.vpilo.babymonitor.network.common.Constants
import org.vpilo.babymonitor.network.common.DiscoveryManager
import org.vpilo.babymonitor.network.common.Endpoints
import java.net.ConnectException
import java.net.InetAddress
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds

internal class DefaultNetworkClientRepository(
    discoveryManager: DiscoveryManager,
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

    override val discoveredServersFlow: Flow<Set<InetAddress>> = discoveryManager.discoveredServers

    private var controlConnectionHandler: ConnectionHandler? = null
    private var audioConnectionHandler: ConnectionHandler? = null
    private var videoConnectionHandler: ConnectionHandler? = null
    private var serverStateJob: Job? = null

    override suspend fun connect(address: InetAddress) {
        controlConnectionHandler?.disconnect()
        controlConnectionHandler = ConnectionHandler(
            connectLambda = {
                networkClient.webSocket(
                    method = HttpMethod.Get,
                    host = address.hostAddress,
                    port = Constants.WEBSOCKET_PORT,
                    path = Endpoints.CONTROL,
                ) {
                    onControlConnectionOpened(address)
                    controlClientWebSocket()
                }
            },
            onDisconnected = { ex -> onControlConnectionClosed(ex) },
        )
            .apply { connect() }

        connectionState.value = NetworkState.Connecting(address)
        Logger.d(TAG) { "Connecting to server at ${address.hostAddress}..." }
    }

    private suspend fun startAudioStream(address: InetAddress) {
        if (audioConnectionHandler != null) {
            Logger.w(TAG) { "Audio stream is already running" }
            return
        }
        audioConnectionHandler = ConnectionHandler(
            connectLambda = {
                networkClient.webSocket(
                    method = HttpMethod.Get,
                    host = address.hostAddress,
                    port = Constants.WEBSOCKET_PORT,
                    path = Endpoints.STREAM_AUDIO,
                ) {
                    //TODO if needed, report whether audio or video are connected or not to the UI
                    audioStreamingClientWebSocket()
                }
            },
            onDisconnected = {
                Logger.i(TAG) { "Audio disconnected, reconnecting" }
                delay(1.seconds)
                audioConnectionHandler?.connect()
            },
        )
            .apply { connect() }
        Logger.d(TAG) { "Audio stream started" }
    }

    private suspend fun startVideoStream(address: InetAddress) {
        if (videoConnectionHandler != null) {
            Logger.w(TAG) { "Video stream is already running" }
            return
        }
        videoConnectionHandler = ConnectionHandler(
            connectLambda = {
                networkClient.webSocket(
                    method = HttpMethod.Get,
                    host = address.hostAddress,
                    port = Constants.WEBSOCKET_PORT,
                    path = Endpoints.STREAM_VIDEO,
                ) {
                    videoStreamingClientWebSocket()
                }
            },
            onDisconnected = {
                Logger.i(TAG) { "Video disconnected, reconnecting" }
                delay(1.seconds)
                videoConnectionHandler?.connect()
            },
        )
            .apply { connect() }
        Logger.d(TAG) { "Video stream started" }
    }

    private suspend fun stopAudioStream() {
        audioConnectionHandler?.disconnect()
        audioConnectionHandler = null
    }

    private suspend fun stopVideoStream() {
        videoConnectionHandler?.disconnect()
        videoConnectionHandler = null
    }

    private suspend fun closeAllConnections() {
        stopAudioStream()
        stopVideoStream()
        controlConnectionHandler?.disconnect()
        controlConnectionHandler = null
        serverStateJob?.cancel()
        serverStateJob = null
    }

    override suspend fun disconnect() {
        closeAllConnections()

        connectionState.value = NetworkState.Disconnected(NetworkState.ErrorReason.ClientQuit)
        Logger.i(TAG) { "Client state: ${connectionState.value}" }
    }

    private fun onControlConnectionOpened(address: InetAddress) {
        connectionState.value = NetworkState.Connected(address)
        Logger.i(TAG) { "Client state: ${connectionState.value}" }

        serverStateJob = scope.launch {
            dataSource.serverState.collect { serverState ->
                Logger.i(TAG) { "Server changed capture mode: ${serverState.captureMode}" }
                when (serverState.captureMode) {
                    CaptureMode.AUDIO_ONLY -> {
                        stopVideoStream()
                        startAudioStream(address)
                    }

                    CaptureMode.VIDEO_ONLY -> {
                        stopAudioStream()
                        startVideoStream(address)
                    }

                    CaptureMode.AUDIO_AND_VIDEO -> {
                        startAudioStream(address)
                        startVideoStream(address)
                    }
                }
            }
        }
    }

    private suspend fun onControlConnectionClosed(exception: Throwable) {
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
