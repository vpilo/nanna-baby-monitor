package org.vpilo.babymonitor.network.server.websockets

import io.ktor.websocket.DefaultWebSocketSession
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.repository.StreamingAudioSenderRepository
import org.vpilo.babymonitor.network.common.protocol.protocolSendAudio
import org.vpilo.babymonitor.network.common.protocol.runWebSocketCatching

internal suspend fun DefaultWebSocketSession.audioStreamingServerWebSocket() =
    coroutineScope {
        val repository = KoinPlatform.getKoin().get<StreamingAudioSenderRepository>()

        val senderJob =
            launch {
                runWebSocketCatching(TAG) {
                    repository.chunks
                        .collect {
                            protocolSendAudio(it)
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

private const val TAG = "NetworkServer-Audio"
