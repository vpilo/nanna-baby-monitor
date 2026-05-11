package org.vpilo.babymonitor.network.client.websockets

import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ClosedSendChannelException
import org.koin.mp.KoinPlatform
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.common.ktx.prettify
import org.vpilo.babymonitor.model.EncodedAudioStreamChunk
import org.vpilo.babymonitor.network.client.NetworkAudioDataSource
import org.vpilo.babymonitor.network.common.protocol.protocolReceiveAudio
import org.vpilo.babymonitor.network.common.protocol.protocolReceiveVideo
import kotlin.coroutines.cancellation.CancellationException

internal suspend fun DefaultClientWebSocketSession.audioStreamingClientWebSocket() {
    val dataSource = KoinPlatform.getKoin().get<NetworkAudioDataSource>()

    Logger.d(TAG) { "WebSocket opened" }

    runCatching {
        while (true) {
            val frame: EncodedAudioStreamChunk = protocolReceiveAudio()
            dataSource.onChunkReceived(frame)
        }
    }.onFailure { ex ->
        if (ex !is CancellationException && ex !is ClosedSendChannelException && ex !is ClosedReceiveChannelException) {
            Logger.w(TAG) { "WebSocket closed: ${ex.prettify()}" }
        } else {
            Logger.d(TAG) { "WebSocket closed" }
        }
    }
}

private const val TAG = "NetworkClient-Audio"
