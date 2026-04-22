package org.vpilo.babymonitor.network.server.websockets

import io.ktor.websocket.DefaultWebSocketSession
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.flow.dropWhile
import org.koin.mp.KoinPlatform
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.repository.StreamingVideoSenderRepository
import org.vpilo.babymonitor.network.common.protocol.protocolSendVideo
import kotlin.coroutines.cancellation.CancellationException

internal suspend fun DefaultWebSocketSession.videoStreamingServerWebSocket() {
    val repository = KoinPlatform.getKoin().get<StreamingVideoSenderRepository>()

    Logger.d(TAG) { "New video streaming client connected" }

    runCatching {
        repository.chunks
            .dropWhile { !it.isKeyFrame }
            .collect {
                protocolSendVideo(it)
            }
    }.onFailure { ex ->
        if (ex !is CancellationException && ex !is ClosedSendChannelException && ex !is ClosedReceiveChannelException) {
            Logger.w(TAG) { "WebSocket closed (${ex::class.simpleName}): ${ex.localizedMessage}" }
        }
    }
}

private const val TAG = "NetworkServer-Video"
