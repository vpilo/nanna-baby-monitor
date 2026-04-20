package org.vpilo.babymonitor.relay

import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
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

    println("Relay running. Press Enter to stop.")
    runBlocking { readlnOrNull() }

    relay.stop()
    koin.close()
    exitProcess(0)
}
