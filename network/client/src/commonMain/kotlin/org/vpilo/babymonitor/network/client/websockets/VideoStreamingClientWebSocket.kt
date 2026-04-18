package org.vpilo.babymonitor.network.client.websockets

import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import org.koin.mp.KoinPlatform
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.network.client.NetworkVideoDataSource
import org.vpilo.babymonitor.network.common.protocol.protocolReceiveVideo

internal suspend fun DefaultClientWebSocketSession.videoStreamingClientWebSocket(): Boolean {
    val dataSource = KoinPlatform.getKoin().get<NetworkVideoDataSource>()

    Logger.d(TAG) { "Connection established" }

    return catchSessionResult(TAG) {
        while (true) {
            val frame = protocolReceiveVideo()
            dataSource.onChunkReceived(frame)
        }
    }
}

private const val TAG = "NetworkClient-Video"
