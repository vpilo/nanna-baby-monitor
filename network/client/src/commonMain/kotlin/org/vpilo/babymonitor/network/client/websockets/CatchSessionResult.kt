package org.vpilo.babymonitor.network.client.websockets

import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ClosedSendChannelException
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.common.ktx.prettify
import kotlin.coroutines.cancellation.CancellationException

internal suspend fun DefaultClientWebSocketSession.catchSessionResult(
    tag: String,
    lambda: suspend DefaultClientWebSocketSession.() -> Unit,
): Boolean =
    runCatching {
        Logger.d(tag) { "WebSocket opened" }
        lambda()
    }.fold(
        onSuccess = { true },
        onFailure = { ex ->
            Logger.d(tag) { "WebSocket closed: ${ex.prettify()}" }
            if (ex is CancellationException) throw ex
            if (ex !is ClosedSendChannelException && ex !is ClosedReceiveChannelException) {
                false
            } else {
                true
            }
        },
    )
