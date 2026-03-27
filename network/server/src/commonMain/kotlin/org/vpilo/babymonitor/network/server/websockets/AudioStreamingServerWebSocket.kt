package org.vpilo.babymonitor.network.server.websockets

import io.ktor.server.websocket.DefaultWebSocketServerSession
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ClosedSendChannelException
import org.koin.mp.KoinPlatform
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.repository.StreamingAudioSenderRepository
import org.vpilo.babymonitor.network.common.protocol.protocolSendAudio
import kotlin.coroutines.cancellation.CancellationException

internal suspend fun DefaultWebSocketServerSession.audioStreamingServerWebSocket() {
    val repository = KoinPlatform.getKoin().get<StreamingAudioSenderRepository>()

    Logger.d(TAG) { "Client connected" }

    runCatching {
        repository.chunks
            .collect {
                protocolSendAudio(it)
            }
    }.onFailure { ex ->
        if (ex !is CancellationException && ex !is ClosedSendChannelException && ex !is ClosedReceiveChannelException) {
            Logger.w(TAG) { "WebSocket closed (${ex::class.simpleName}): ${ex.localizedMessage}" }
        }
    }
}

private const val TAG = "NetworkServer-Audio"
