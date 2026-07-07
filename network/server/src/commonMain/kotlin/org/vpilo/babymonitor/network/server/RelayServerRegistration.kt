package org.vpilo.babymonitor.network.server

import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.wss
import io.ktor.http.HttpMethod
import io.ktor.http.encodeURLPathPart
import io.ktor.websocket.Frame
import io.ktor.websocket.pingInterval
import io.ktor.websocket.readText
import io.ktor.websocket.timeout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.common.ktx.prettify
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.network.common.Constants
import org.vpilo.babymonitor.network.common.Endpoints
import org.vpilo.babymonitor.network.common.RelaySignals
import org.vpilo.babymonitor.network.common.discovery.ktx.asTransportString
import org.vpilo.babymonitor.network.common.protocol.runWebSocketCatching
import org.vpilo.babymonitor.network.common.relayHttpClient
import org.vpilo.babymonitor.network.server.websockets.audioStreamingServerWebSocket
import org.vpilo.babymonitor.network.server.websockets.controlServerWebSocket
import org.vpilo.babymonitor.network.server.websockets.videoStreamingServerWebSocket
import kotlin.coroutines.CoroutineContext

internal class RelayServerRegistration(
    coroutineContext: CoroutineContext,
) {
    private val scope: CoroutineScope = CoroutineScope(coroutineContext + SupervisorJob())

    private val _isRegistered = MutableStateFlow(false)
    val isRegistered: Flow<Boolean> = _isRegistered.asStateFlow()

    private var relayHost: String = ""
    private lateinit var server: Device.LocalServer
    private var registrationJob: Job? = null
    private val activeStreamJobs = java.util.concurrent.CopyOnWriteArrayList<Job>()

    private var isEnabled: Boolean = true

    fun setRelayHost(host: String) {
        if (relayHost == host) return
        relayHost = host
        restart()
    }

    fun identifySelf(server: Device.LocalServer) {
        if (this::server.isInitialized && this.server == server) return
        this.server = server
        restart()
    }

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        restart()
    }

    fun stop() {
        _isRegistered.value = false
        registrationJob?.cancel()
        activeStreamJobs.forEach { it.cancel() }
        activeStreamJobs.clear()
        registrationJob = null
    }

    fun restart() {
        stop()
        if (!isEnabled || relayHost.isEmpty() || !this::server.isInitialized) return
        registrationJob = scope.launch { runRegistrationLoop() }
    }

    private suspend fun runRegistrationLoop() {
        while (true) {
            Logger.d(TAG) { "Connecting to relay at $relayHost as $server" }
            runCatching {
                relayHttpClient.wss(
                    method = HttpMethod.Get,
                    host = relayHost,
                    port = Constants.RELAY_PORT,
                    path = Endpoints.Relay.SERVER_REGISTRATION,
                ) {
                    pingInterval = Constants.WEBSOCKET_PING_PERIOD
                    timeout = Constants.WEBSOCKET_TIMEOUT

                    runWebSocketCatching(TAG) {
                        // TODO authentication
                        send(Frame.Text(server.asTransportString(relayHost)))
                        Logger.i(TAG) { "Registered with relay as $server" }
                        _isRegistered.value = true
                        readRelaySignals()
                    }
                }
            }.onFailure { ex ->
                if (ex is CancellationException) throw ex
                Logger.i(TAG) { "Relay disconnected: ${ex.prettify()}. Retrying." }
            }
            activeStreamJobs.forEach { it.cancel() }
            activeStreamJobs.clear()
            _isRegistered.value = false
            delay(Constants.RECONNECTION_TIMEOUT)
        }
    }

    private suspend fun DefaultClientWebSocketSession.readRelaySignals() {
        for (frame in incoming) {
            if (frame !is Frame.Text) continue
            when (frame.readText()) {
                RelaySignals.CONTROL -> launchStream(Endpoints.CONTROL)
                RelaySignals.AUDIO -> launchStream(Endpoints.STREAM_AUDIO)
                RelaySignals.VIDEO -> launchStream(Endpoints.STREAM_VIDEO)
            }
        }
    }

    private fun launchStream(endpoint: String) {
        val streamPath =
            when (endpoint) {
                Endpoints.CONTROL -> Endpoints.Relay.SERVER_CONTROL
                Endpoints.STREAM_AUDIO -> Endpoints.Relay.SERVER_AUDIO
                Endpoints.STREAM_VIDEO -> Endpoints.Relay.SERVER_VIDEO
                else -> error("Unknown endpoint: $endpoint")
            }
        val job =
            scope.launch {
                try {
                    relayHttpClient.wss(
                        method = HttpMethod.Get,
                        host = relayHost,
                        port = Constants.RELAY_PORT,
                        path = "$streamPath/${server.idString.encodeURLPathPart()}",
                    ) {
                        pingInterval = Constants.WEBSOCKET_PING_PERIOD
                        timeout = Constants.WEBSOCKET_TIMEOUT

                        when (endpoint) {
                            Endpoints.CONTROL -> controlServerWebSocket(serverDeviceId = server.id)
                            Endpoints.STREAM_AUDIO -> audioStreamingServerWebSocket(serverDeviceId = server.id)
                            Endpoints.STREAM_VIDEO -> videoStreamingServerWebSocket(serverDeviceId = server.id)
                        }
                    }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (
                    @Suppress("TooGenericExceptionCaught") ex: Exception,
                ) {
                    Logger.w(TAG) { "Relay stream $endpoint failed: ${ex.prettify()}" }
                }
            }
        activeStreamJobs.add(job)
        job.invokeOnCompletion { activeStreamJobs.remove(job) }
    }

    private companion object {
        private val TAG = RelayServerRegistration::class
    }
}
