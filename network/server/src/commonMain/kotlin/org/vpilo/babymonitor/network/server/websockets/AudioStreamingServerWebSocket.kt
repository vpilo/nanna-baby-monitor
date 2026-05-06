package org.vpilo.babymonitor.network.server.websockets

import io.ktor.websocket.DefaultWebSocketSession
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ClosedSendChannelException
import org.koin.mp.KoinPlatform
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.common.ktx.prettify
import org.vpilo.babymonitor.model.repository.StreamingAudioSenderRepository
import org.vpilo.babymonitor.network.common.protocol.protocolSendAudio
import kotlin.coroutines.cancellation.CancellationException

internal suspend fun DefaultWebSocketSession.audioStreamingServerWebSocket() {
    val repository = KoinPlatform.getKoin().get<StreamingAudioSenderRepository>()

    Logger.d(TAG) { "WebSocket opened" }

    runCatching {
        repository.chunks
            .collect {
                protocolSendAudio(it)
            }
    }.onFailure { ex ->
        if (ex !is CancellationException && ex !is ClosedSendChannelException && ex !is ClosedReceiveChannelException) {
            Logger.d(TAG) { "WebSocket closed: ${ex.prettify()}" }
        } else {
            Logger.d(TAG) { "WebSocket closed" }
        }
    }
}

private const val TAG = "NetworkServer-Audio"
