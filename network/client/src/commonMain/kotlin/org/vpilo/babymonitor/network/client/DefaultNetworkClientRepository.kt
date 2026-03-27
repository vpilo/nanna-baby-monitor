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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.repository.NetworkClientRepository
import org.vpilo.babymonitor.model.repository.NetworkState
import org.vpilo.babymonitor.network.client.websockets.audioStreamingClientWebSocket
import org.vpilo.babymonitor.network.client.websockets.videoStreamingClientWebSocket
import org.vpilo.babymonitor.network.common.Constants
import org.vpilo.babymonitor.network.common.DiscoveryManager
import org.vpilo.babymonitor.network.common.Endpoints
import java.net.ConnectException
import java.net.InetAddress
import kotlin.coroutines.CoroutineContext

internal class DefaultNetworkClientRepository(
    discoveryManager: DiscoveryManager,
    private val coroutineContext: CoroutineContext,
) : NetworkClientRepository {
    private val scope = CoroutineScope(SupervisorJob() + coroutineContext)

    private val networkClient: HttpClient by lazy {
        HttpClient(CIO) {
            install(WebSockets)
        }
    }

    private val state: MutableStateFlow<NetworkState> =
        MutableStateFlow(NetworkState.Disconnected(NetworkState.ErrorReason.NotConnectedYet))
    override val stateFlow: Flow<NetworkState> = state.asStateFlow()

    override val discoveredServers: Flow<Set<InetAddress>> = discoveryManager.discoveredServers

    private var audioStreamJob: Job? = null
    private var videoStreamJob: Job? = null

    override suspend fun connect(address: InetAddress) {
        audioStreamJob =
            scope.launch {
                @Suppress("TooGenericExceptionCaught")
                try {
                    networkClient.webSocket(
                        method = HttpMethod.Get,
                        host = address.hostAddress,
                        port = Constants.WEBSOCKET_PORT,
                        path = Endpoints.STREAM_AUDIO,
                    ) {
                        onConnectionOpened(address, isAudio = true)
                        audioStreamingClientWebSocket()
                    }
                } catch (ex: Exception) {
                    onConnectionClosed(ex)
                }
            }

        videoStreamJob =
            scope.launch {
                @Suppress("TooGenericExceptionCaught")
                try {
                    networkClient.webSocket(
                        method = HttpMethod.Get,
                        host = address.hostAddress,
                        port = Constants.WEBSOCKET_PORT,
                        path = Endpoints.STREAM_VIDEO,
                    ) {
                        onConnectionOpened(address, isAudio = false)
                        videoStreamingClientWebSocket()
                    }
                } catch (ex: Exception) {
                    onConnectionClosed(ex)
                }
            }
        state.value = NetworkState.Connecting(address)
        Logger.d(TAG) { "Connecting to server at ${address.hostAddress}..." }
    }

    override suspend fun disconnect() {
        audioStreamJob?.cancel()
        audioStreamJob = null
        videoStreamJob?.cancel()
        videoStreamJob = null
        state.value = NetworkState.Disconnected(NetworkState.ErrorReason.ClientQuit)
        Logger.i(TAG) { "Client state: ${state.value}" }
    }

    private fun onConnectionOpened(
        address: InetAddress,
        isAudio: Boolean,
    ) {
        val old = state.value
        state.value =
            when (old) {
                is NetworkState.Connected -> {
                    val newAudioState = isAudio || old.hasAudio
                    val newVideoState = !isAudio || old.hasVideo
                    NetworkState.Connected(address, newAudioState, newVideoState)
                }

                else -> {
                    NetworkState.Connected(address, hasAudio = isAudio, hasVideo = !isAudio)
                }
            }
        Logger.i(TAG) { "Client state: ${state.value}" }
    }

    private fun onConnectionClosed(exception: Throwable) {
        audioStreamJob?.cancel()
        audioStreamJob = null
        videoStreamJob?.cancel()
        videoStreamJob = null

        state.value =
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
        Logger.i(TAG) { "Client state: ${state.value}" }
    }

    private companion object {
        private val TAG = DefaultNetworkClientRepository::class
    }
}
