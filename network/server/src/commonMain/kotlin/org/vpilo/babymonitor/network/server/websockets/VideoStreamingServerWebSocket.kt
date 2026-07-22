package org.vpilo.babymonitor.network.server.websockets

import io.ktor.websocket.DefaultWebSocketSession
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.model.repository.StreamingVideoSenderRepository
import org.vpilo.babymonitor.network.common.protocol.StreamType
import org.vpilo.babymonitor.network.common.protocol.protocolSendVideo
import org.vpilo.babymonitor.network.common.protocol.runWebSocketCatching
import org.vpilo.babymonitor.network.model.repository.ActiveSessionsRepository
import org.vpilo.babymonitor.network.model.repository.PairingRepository
import org.vpilo.babymonitor.network.server.pairing.DefaultActiveSessionsRepository
import org.vpilo.babymonitor.network.server.session.serverSessionHandshake

internal suspend fun DefaultWebSocketSession.videoStreamingServerWebSocket(serverDeviceId: DeviceId) {
    val pairingRepository = KoinPlatform.getKoin().get<PairingRepository>()

    Logger.d(TAG) { "WebSocket opened" }

    val handshake = serverSessionHandshake(serverDeviceId, pairingRepository, StreamType.VIDEO) ?: return

    val sessionRegistry = KoinPlatform.getKoin().get<ActiveSessionsRepository>() as DefaultActiveSessionsRepository
    sessionRegistry.register(handshake.clientId, this)
    try {
        coroutineScope {
            val repository = KoinPlatform.getKoin().get<StreamingVideoSenderRepository>()

            val senderJob =
                launch {
                    runWebSocketCatching(TAG) {
                        repository.chunks
                            .dropWhile {
                                val drop = !it.isKeyFrame
                                if (drop) Logger.d(TAG) { "Dropping non-keyframe chunk while waiting for first keyframe" }
                                drop
                            }.collect {
                                protocolSendVideo(it, handshake.cipher)
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
    } finally {
        sessionRegistry.unregister(handshake.clientId, this)
    }
}

private const val TAG = "NetworkServer-Video"
