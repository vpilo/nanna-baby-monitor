package org.vpilo.babymonitor.relay

import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.vpilo.babymonitor.common.BuildInfo
import org.vpilo.babymonitor.network.relay.DefaultNetworkRelayRepository
import org.vpilo.babymonitor.network.relay.di.networkRelayKoinModule
import kotlin.system.exitProcess

fun main() {
    val koin =
        startKoin {
            modules(networkRelayKoinModule)
        }.koin

    val relay = koin.get<DefaultNetworkRelayRepository>()
    relay.start()

    println("Baby Monitor relay version ${BuildInfo.VERSION} running. Write `quit` to stop.")

    runBlocking {
        var input: String?
        do {
            input = readlnOrNull()
        } while (input?.lowercase()?.trim() != "quit")
    }

    relay.stop()
    koin.close()
    exitProcess(0)
}
