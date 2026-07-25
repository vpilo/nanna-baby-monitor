package org.vpilo.babymonitor.relay

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.vpilo.babymonitor.common.BuildInfo
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.data.di.dataKoinModule
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.model.repository.toDeviceId
import org.vpilo.babymonitor.settings.data.di.settingsDataKoinModule
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.repository.SettingsRepository
import org.vpilo.babymonitor.settings.model.settings.DeviceId
import kotlin.coroutines.CoroutineContext
import kotlin.system.exitProcess

private const val TAG = "Relay"

private fun babyMonitorMain(args: Array<String>) {
    val koin =
        startKoin {
            modules(
                module {
                    single<CoroutineContext> { Dispatchers.Default }
                },
                dataKoinModule,
                settingsDataKoinModule,
            )
        }.koin

    if (args.isEmpty()) {
        Logger.e(TAG) { "Relay host name argument not provided" }
        println(
            "The Baby Monitor relay needs one argument: an hostname." +
                " It can be either a public IP address, or a hostname from a domain or a dynamic DNS service.",
        )
        exitProcess(1)
    }
    if (args.size > 1) {
        Logger.e(TAG) { "Too many arguments provided" }
        println("The Baby Monitor relay needs only one argument: the hostname.")
        exitProcess(1)
    }
    val hostName = args.first().trim()
    if (hostName.isBlank()) {
        Logger.e(TAG) { "Invalid relay host name argument" }
        println(
            "The Baby Monitor relay needs one argument: an hostname." +
                " It can be either a public IP address, or a hostname from a domain or a dynamic DNS service.",
        )
        exitProcess(1)
    }

    val coroutineContext: CoroutineContext = koin.get()
    val coroutineScope = CoroutineScope(SupervisorJob() + coroutineContext)
    val settings: SettingsRepository = koin.get()

    val deferredDeviceId =
        coroutineScope.async {
            val settingDeviceId = settings.load(Setting.DeviceId)
            // Ensure that the device has a unique ID and a name, as it's required for the server to be discoverable by clients.
            settingDeviceId
                .ifBlank {
                    DeviceId
                        .random()
                        .toString()
                        .also { settings.save(Setting.DeviceId, it) }
                }.toDeviceId()
        }

    val relay = DefaultNetworkRelayRepository()
    coroutineScope.launch {
        val device =
            Device.Relay(
                id = deferredDeviceId.await(),
                name = hostName,
                relayHost = hostName,
            )
        relay.start(device)
    }

    Logger.i(TAG) { "Baby Monitor relay version ${BuildInfo.VERSION} running. Press Ctrl+C to end." }

    SignalHandler.awaitSignal {
        Logger.i(TAG) { "Shutting down on signal $it" }
        relay.stop()
        coroutineScope.cancel()
        koin.close()
        exitProcess(0)
    }
}

fun main(args: Array<String>) {
    babyMonitorMain(args)
}

@Suppress("MemberNameEqualsClassName")
class Main private constructor() {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) = babyMonitorMain(args)
    }
}
