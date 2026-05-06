package org.vpilo.babymonitor.network.client.websockets

import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import org.koin.mp.KoinPlatform
import org.vpilo.babymonitor.model.EncodedAudioStreamChunk
import org.vpilo.babymonitor.network.client.NetworkAudioDataSource
import org.vpilo.babymonitor.network.common.protocol.protocolReceiveAudio

internal suspend fun DefaultClientWebSocketSession.audioStreamingClientWebSocket() {
    val dataSource = KoinPlatform.getKoin().get<NetworkAudioDataSource>()

    while (true) {
        val frame: EncodedAudioStreamChunk = protocolReceiveAudio()
        dataSource.onChunkReceived(frame)
    }
}
