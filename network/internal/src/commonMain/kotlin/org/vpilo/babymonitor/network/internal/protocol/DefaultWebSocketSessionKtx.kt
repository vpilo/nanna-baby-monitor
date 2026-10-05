package org.vpilo.babymonitor.network.internal.protocol

import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.WebSocketSession
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ClosedSendChannelException
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.common.ktx.prettify
import kotlin.coroutines.cancellation.CancellationException

suspend inline fun DefaultWebSocketSession.runWebSocketCatching(
    tag: String,
    block: WebSocketSession.() -> Unit,
) {
    try {
        Logger.d(tag) { "WebSocket opened" }
        block()
        Logger.d(tag) { "WebSocket closed normally" }
    } catch (ex: CancellationException) {
        throw ex
    } catch (_: ClosedSendChannelException) {
        val reason = closeReason.await() ?: "none"
        Logger.i(tag) { "WebSocket closed: $reason" }
    } catch (_: ClosedReceiveChannelException) {
        val reason = closeReason.await() ?: "none"
        Logger.i(tag) { "WebSocket closed: $reason" }
    } catch (
        @Suppress("TooGenericExceptionCaught") ex: Throwable,
    ) {
        val reason = closeReason.await() ?: "none"
        Logger.w(tag) { "WebSocket closed: ${ex.prettify()} (reason: $reason)" }
    }
}
