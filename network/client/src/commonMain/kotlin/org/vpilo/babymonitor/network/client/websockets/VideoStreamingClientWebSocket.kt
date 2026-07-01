package org.vpilo.babymonitor.network.client.websockets

import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import org.koin.mp.KoinPlatform
import org.vpilo.babymonitor.network.client.NetworkVideoDataSource
import org.vpilo.babymonitor.network.common.protocol.protocolReceiveVideo
import org.vpilo.babymonitor.network.common.protocol.runWebSocketCatching

internal suspend fun DefaultClientWebSocketSession.videoStreamingClientWebSocket() {
    val dataSource = KoinPlatform.getKoin().get<NetworkVideoDataSource>()

    runWebSocketCatching(TAG) {
        while (true) {
            val frame = protocolReceiveVideo()
            dataSource.onChunkReceived(frame)
        }
    }
}

private const val TAG = "NetworkClient-Video"
