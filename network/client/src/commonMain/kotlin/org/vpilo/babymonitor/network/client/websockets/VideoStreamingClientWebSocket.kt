package org.vpilo.babymonitor.network.client.websockets

import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import org.koin.mp.KoinPlatform
import org.vpilo.babymonitor.network.client.NetworkVideoDataSource
import org.vpilo.babymonitor.network.common.protocol.protocolReceiveVideo

internal suspend fun DefaultClientWebSocketSession.videoStreamingClientWebSocket() {
    val dataSource = KoinPlatform.getKoin().get<NetworkVideoDataSource>()
    while (true) {
        val frame = protocolReceiveVideo()
        dataSource.onChunkReceived(frame)
    }
}
