package org.vpilo.babymonitor.network.client.websockets

import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.receiveDeserialized
import org.koin.mp.KoinPlatform
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.EncodedAudioStreamChunk
import org.vpilo.babymonitor.network.client.NetworkAudioDataSource

internal suspend fun DefaultClientWebSocketSession.audioStreamingClientWebSocket() {
    Logger.w(TAG) { "WebSocket connection established with the server." }

    val dataSource = KoinPlatform.getKoin().get<NetworkAudioDataSource>()

    while (true) {
        val frame = receiveDeserialized<EncodedAudioStreamChunk>()
        dataSource.onChunkReceived(frame)
    }
}

private const val TAG = "NetworkClient-Audio"
