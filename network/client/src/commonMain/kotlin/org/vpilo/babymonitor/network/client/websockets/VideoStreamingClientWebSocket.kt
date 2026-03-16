package org.vpilo.babymonitor.network.client.websockets

import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import org.koin.mp.KoinPlatform
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.EncodedVideoStreamChunk
import org.vpilo.babymonitor.network.client.NetworkVideoDataSource
import org.vpilo.babymonitor.network.common.protocol.protocolReceiveVideo


internal suspend fun DefaultClientWebSocketSession.videoStreamingClientWebSocket() {
    Logger.w(TAG) { "WebSocket connection established with the server." }

    val dataSource = KoinPlatform.getKoin().get<NetworkVideoDataSource>()

    while (true) {
        val frame = protocolReceiveVideo()
        dataSource.onChunkReceived(frame)
    }
}

private const val TAG = "NetworkClient-Video"
