package org.vpilo.babymonitor.network.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets

internal val networkClient: HttpClient by lazy {
    HttpClient(CIO) {
        install(WebSockets.Plugin)
    }
}
