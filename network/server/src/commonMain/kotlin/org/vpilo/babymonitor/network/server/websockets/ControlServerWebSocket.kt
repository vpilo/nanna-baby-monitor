package org.vpilo.babymonitor.network.server.websockets

import io.ktor.websocket.DefaultWebSocketSession
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.common.ktx.prettify
import org.vpilo.babymonitor.model.repository.NetworkServerRepository
import org.vpilo.babymonitor.network.common.protocol.makeServerMessageFrame
import kotlin.coroutines.cancellation.CancellationException

internal suspend fun DefaultWebSocketSession.controlServerWebSocket() {
    val repository = KoinPlatform.getKoin().get<NetworkServerRepository>()

    Logger.d(TAG) { "New client connected" }

    val stateSendingJob =
        launch {
            repository.serverStateFlow.collect { state ->
                send(makeServerMessageFrame(state))
            }
        }

    runCatching {
        incoming.consumeEach {
            Logger.w(TAG) { "Unexpected message received from client: $it" }
        }
    }.onFailure { ex ->
        if (ex !is CancellationException && ex !is ClosedSendChannelException && ex !is ClosedReceiveChannelException) {
            Logger.i(TAG) { "WebSocket closed: ${ex.prettify()}" }
        }
    }.also {
        stateSendingJob.cancel()
    }
}

private const val TAG = "NetworkServer-Control"
