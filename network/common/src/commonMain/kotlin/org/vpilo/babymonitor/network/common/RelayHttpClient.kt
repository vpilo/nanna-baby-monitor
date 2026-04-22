package org.vpilo.babymonitor.network.common

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.pingInterval

val relayHttpClient: HttpClient by lazy {
    HttpClient(CIO) {
        install(WebSockets) {
            pingInterval = Constants.WEBSOCKET_PING_PERIOD
        }
        engine {
            https {
                trustManager = RelayTrustManager()
            }
        }
    }
}
