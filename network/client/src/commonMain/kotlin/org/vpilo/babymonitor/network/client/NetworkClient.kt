package org.vpilo.babymonitor.network.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.pingInterval
import org.vpilo.babymonitor.network.common.Constants

internal val networkClient: HttpClient by lazy {
    HttpClient(CIO) {
        install(WebSockets.Plugin) {
            pingInterval = Constants.WEBSOCKET_PING_PERIOD
        }
    }
}
