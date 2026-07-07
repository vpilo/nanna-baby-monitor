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
import org.vpilo.babymonitor.network.server.session.serverSessionHandshake
import org.vpilo.babymonitor.settings.model.repository.PairingRepository

internal suspend fun DefaultWebSocketSession.videoStreamingServerWebSocket(serverDeviceId: DeviceId) =
    coroutineScope {
        val repository = KoinPlatform.getKoin().get<StreamingVideoSenderRepository>()
        val pairingRepository = KoinPlatform.getKoin().get<PairingRepository>()

        Logger.d(TAG) { "WebSocket opened" }

        val cipher = serverSessionHandshake(serverDeviceId, pairingRepository, StreamType.VIDEO) ?: return@coroutineScope

        val senderJob =
            launch {
                runWebSocketCatching(TAG) {
                    repository.chunks
                        .dropWhile {
                            val drop = !it.isKeyFrame
                            if (drop) Logger.d(TAG) { "Dropping non-keyframe chunk while waiting for first keyframe" }
                            drop
                        }.collect {
                            protocolSendVideo(it, cipher)
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

private const val TAG = "NetworkServer-Video"
