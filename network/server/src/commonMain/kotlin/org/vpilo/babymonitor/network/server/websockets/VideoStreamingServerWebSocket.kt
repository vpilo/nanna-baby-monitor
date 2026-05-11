package org.vpilo.babymonitor.network.server.websockets

import io.ktor.websocket.DefaultWebSocketSession
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.common.ktx.prettify
import org.vpilo.babymonitor.model.repository.StreamingVideoSenderRepository
import org.vpilo.babymonitor.network.common.protocol.protocolSendVideo
import kotlin.coroutines.cancellation.CancellationException

internal suspend fun DefaultWebSocketSession.videoStreamingServerWebSocket() =
    coroutineScope {
        val repository = KoinPlatform.getKoin().get<StreamingVideoSenderRepository>()

        Logger.d(TAG) { "WebSocket opened" }

        val senderJob =
            launch {
                runCatching {
                    repository.chunks
                        .dropWhile {
                            val drop = !it.isKeyFrame
                            if (drop) Logger.d(TAG) { "Dropping non-keyframe chunk while waiting for first keyframe" }
                            drop
                        }.collect {
                            protocolSendVideo(it)
                        }
                }.onFailure { ex ->
                    if (ex !is CancellationException && ex !is ClosedSendChannelException && ex !is ClosedReceiveChannelException) {
                        Logger.w(TAG) { "WebSocket closed: ${ex.prettify()}" }
                    } else {
                        Logger.d(TAG) { "WebSocket closed" }
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
