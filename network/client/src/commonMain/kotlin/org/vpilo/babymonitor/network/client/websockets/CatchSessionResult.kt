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
    runCatching { lambda() }
        .fold(
            onSuccess = { true },
            onFailure = { ex ->
                if (ex is CancellationException) throw ex
                if (ex !is ClosedSendChannelException && ex !is ClosedReceiveChannelException) {
                    Logger.i(tag) { "WebSocket closed: ${ex.prettify()}" }
                    false
                } else {
                    true
                }
            },
        )
