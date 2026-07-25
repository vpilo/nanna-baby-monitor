package org.vpilo.babymonitor.network.internal

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.pingInterval
import org.vpilo.babymonitor.network.model.Constants

val relayHttpClient: HttpClient by lazy {
    HttpClient(CIO) {
        install(WebSockets) {
            pingInterval = Constants.WEBSOCKET_PING_PERIOD
        }
    }
}
