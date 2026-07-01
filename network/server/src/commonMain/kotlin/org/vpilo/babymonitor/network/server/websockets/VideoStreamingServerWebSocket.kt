package org.vpilo.babymonitor.network.server.websockets

import io.ktor.websocket.DefaultWebSocketSession
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.repository.StreamingVideoSenderRepository
import org.vpilo.babymonitor.network.common.protocol.protocolSendVideo
import org.vpilo.babymonitor.network.common.protocol.runWebSocketCatching

internal suspend fun DefaultWebSocketSession.videoStreamingServerWebSocket() =
    coroutineScope {
        val repository = KoinPlatform.getKoin().get<StreamingVideoSenderRepository>()

        Logger.d(TAG) { "WebSocket opened" }

        val senderJob =
            launch {
                runWebSocketCatching(TAG) {
                    repository.chunks
                        .dropWhile {
                            val drop = !it.isKeyFrame
                            if (drop) Logger.d(TAG) { "Dropping non-keyframe chunk while waiting for first keyframe" }
                            drop
                        }.collect {
                            protocolSendVideo(it)
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
