package org.vpilo.babymonitor.network.client.websockets

import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import org.koin.mp.KoinPlatform
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.network.client.NetworkControlDataSource
import org.vpilo.babymonitor.network.common.protocol.ServerMessage
import org.vpilo.babymonitor.network.common.protocol.receiveServerMessage

internal suspend fun DefaultClientWebSocketSession.controlClientWebSocket(): Boolean {
    val dataSource = KoinPlatform.getKoin().get<NetworkControlDataSource>()

    return catchSessionResult(TAG) {
        while (true) {
            when (val message = receiveServerMessage()) {
                is ServerMessage.State -> dataSource.onServerStateReceived(message.payload)
            }
        }
    }
}

private const val TAG = "NetworkClient-Control"
