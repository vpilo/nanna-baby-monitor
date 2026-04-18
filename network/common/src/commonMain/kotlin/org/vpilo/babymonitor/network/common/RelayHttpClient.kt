package org.vpilo.babymonitor.network.common

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets

val relayHttpClient: HttpClient by lazy {
    HttpClient(CIO) {
        install(WebSockets)
        engine {
            https {
                trustManager = RelayTrustManager()
            }
        }
    }
}
