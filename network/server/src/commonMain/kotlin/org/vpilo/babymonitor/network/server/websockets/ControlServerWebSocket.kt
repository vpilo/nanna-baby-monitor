package org.vpilo.babymonitor.network.server.websockets

import io.ktor.websocket.DefaultWebSocketSession
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.model.repository.NetworkServerRepository
import org.vpilo.babymonitor.network.common.protocol.StreamType
import org.vpilo.babymonitor.network.common.protocol.runWebSocketCatching
import org.vpilo.babymonitor.network.common.protocol.sendServerMessage
import org.vpilo.babymonitor.network.server.session.serverSessionHandshake
import org.vpilo.babymonitor.settings.model.repository.PairingRepository

internal suspend fun DefaultWebSocketSession.controlServerWebSocket(serverDeviceId: DeviceId) =
    coroutineScope {
        val repository = KoinPlatform.getKoin().get<NetworkServerRepository>()
        val pairingRepository = KoinPlatform.getKoin().get<PairingRepository>()
        val cipher = serverSessionHandshake(serverDeviceId, pairingRepository, StreamType.CONTROL) ?: return@coroutineScope

        val senderJob =
            launch {
                runWebSocketCatching(TAG) {
                    repository.serverStateFlow.collect { state ->
                        sendServerMessage(state, cipher)
                    }
                }
            }

        // Drain incoming only to detect session closure.
        val readerJob =
            launch {
                for (frame in incoming) {
                    Logger.w(TAG) { "Unexpected frame from client: $frame" }
                }
            }

        senderJob.invokeOnCompletion { readerJob.cancel() }
        readerJob.invokeOnCompletion { senderJob.cancel() }
    }

private const val TAG = "NetworkServer-Control"
