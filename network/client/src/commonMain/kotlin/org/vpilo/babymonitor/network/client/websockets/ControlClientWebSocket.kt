package org.vpilo.babymonitor.network.client.websockets

import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.network.client.NetworkControlDataSource
import org.vpilo.babymonitor.network.common.protocol.ServerMessage
import org.vpilo.babymonitor.network.common.protocol.receiveServerMessage
import kotlin.coroutines.cancellation.CancellationException

internal suspend fun DefaultClientWebSocketSession.controlClientWebSocket() {
    val dataSource = KoinPlatform.getKoin().get<NetworkControlDataSource>()

    Logger.d(TAG) { "Connection established" }

    val frameSenderJob = launch {
        // TODO: Handle sending messages to server
    }

    runCatching {
        while (true) {
            when (val message = receiveServerMessage()) {
                is ServerMessage.State -> dataSource.onServerStateReceived(message.payload)
            }
        }
    }.onFailure { ex ->
        if (ex !is CancellationException && ex !is ClosedSendChannelException && ex !is ClosedReceiveChannelException) {
            Logger.w(TAG) { "WebSocket closed (${ex::class.simpleName}): ${ex.localizedMessage}" }
        }
    }.also {
        frameSenderJob.cancel()
    }
}

private const val TAG = "NetworkClient-Control"
