package org.vpilo.babymonitor.network.server

import io.ktor.server.application.Application
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


suspend fun createNetworkServer() {
    CoroutineScope(Dispatchers.IO).launch {
        embeddedServer(CIO, port = COMMUNICATION_PORT, host = "0.0.0.0", module = Application::module)
            .start(wait = true)
    }
}

fun Application.module() {
    configureSockets()
//    configureSerialization()
//    configureRouting()
}

private const val COMMUNICATION_PORT = 47812

private const val TAG = "NetworkServer"
