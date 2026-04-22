package org.vpilo.babymonitor.network.server

import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.wss
import io.ktor.http.HttpMethod
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.network.common.Constants
import org.vpilo.babymonitor.network.common.Endpoints
import org.vpilo.babymonitor.network.common.RelayHandshake
import org.vpilo.babymonitor.network.common.RelaySignals
import org.vpilo.babymonitor.network.common.deriveSharedRelaySecret
import org.vpilo.babymonitor.network.common.relayHttpClient
import org.vpilo.babymonitor.network.server.websockets.audioStreamingServerWebSocket
import org.vpilo.babymonitor.network.server.websockets.controlServerWebSocket
import org.vpilo.babymonitor.network.server.websockets.videoStreamingServerWebSocket
import kotlin.time.Duration.Companion.seconds

internal class RelayServerRegistration(
    private val scope: CoroutineScope,
) {
    private var relayHost: String = ""
    private var deviceName: String = ""
    private var registrationJob: Job? = null
    private val activeStreamJobs = mutableListOf<Job>()

    fun setRelayHost(host: String) {
        relayHost = host
        restart()
    }

    fun setDeviceName(name: String) {
        deviceName = name
        restart()
    }

    private fun restart() {
        registrationJob?.cancel()
        activeStreamJobs.forEach { it.cancel() }
        activeStreamJobs.clear()
        registrationJob = null
        if (relayHost.isEmpty() || deviceName.isEmpty()) return
        registrationJob = scope.launch { runRegistrationLoop() }
    }

    private suspend fun runRegistrationLoop() {
        while (true) {
            @Suppress("TooGenericExceptionCaught")
            try {
                Logger.d(TAG) { "Connecting to relay at $relayHost as '$deviceName'" }
                relayHttpClient.wss(
                    method = HttpMethod.Get,
                    host = relayHost,
                    port = Constants.RELAY_PORT,
                    path = "/relay/server",
                ) {
                    RelayHandshake.send(this, secret)
                    send(Frame.Text(deviceName))
                    Logger.i(TAG) { "Registered with relay as '$deviceName'" }
                    readRelaySignals()
                }
            } catch (e: CancellationException) {
                Logger.d(TAG) { "Relay registration cancelled" }
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Logger.w(TAG) { "Relay registration disconnected: ${e.message}. Retrying in 5s." }
                activeStreamJobs.forEach { it.cancel() }
                activeStreamJobs.clear()
                delay(5.seconds)
            }
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
        val job =
            scope.launch {
                @Suppress("TooGenericExceptionCaught")
                try {
                    relayHttpClient.wss(
                        method = HttpMethod.Get,
                        host = relayHost,
                        port = Constants.RELAY_PORT,
                        path = "/relay/server$endpoint/$deviceName",
                    ) {
                        RelayHandshake.send(this, secret)
                        when (endpoint) {
                            Endpoints.CONTROL -> controlServerWebSocket()
                            Endpoints.STREAM_AUDIO -> audioStreamingServerWebSocket()
                            Endpoints.STREAM_VIDEO -> videoStreamingServerWebSocket()
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Logger.w(TAG) { "Relay stream $endpoint failed: ${e.message}" }
                }
            }
        activeStreamJobs.add(job)
        job.invokeOnCompletion { activeStreamJobs.remove(job) }
    }

    private companion object {
        private val TAG = RelayServerRegistration::class
        private val secret by lazy { deriveSharedRelaySecret() }
    }
}
