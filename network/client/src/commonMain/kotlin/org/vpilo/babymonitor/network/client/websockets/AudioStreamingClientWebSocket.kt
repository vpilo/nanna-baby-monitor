package org.vpilo.babymonitor.network.client.websockets

import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import org.koin.mp.KoinPlatform
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.EncodedAudioStreamChunk
import org.vpilo.babymonitor.network.client.NetworkAudioDataSource
import org.vpilo.babymonitor.network.common.protocol.protocolReceiveAudio

internal suspend fun DefaultClientWebSocketSession.audioStreamingClientWebSocket(): Boolean {
    val dataSource = KoinPlatform.getKoin().get<NetworkAudioDataSource>()

    Logger.d(TAG) { "Connection established" }

    return catchSessionResult(TAG) {
        while (true) {
            val frame: EncodedAudioStreamChunk = protocolReceiveAudio()
            dataSource.onChunkReceived(frame)
        }
    }
}

private const val TAG = "NetworkClient-Audio"
