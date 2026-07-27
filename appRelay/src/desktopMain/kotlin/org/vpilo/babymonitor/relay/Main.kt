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
import org.vpilo.babymonitor.network.model.RelayConfiguration
import org.vpilo.babymonitor.network.security.crypto.deriveRelayAccessKey
import org.vpilo.babymonitor.settings.data.di.settingsDataKoinModule
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.repository.SettingsRepository
import org.vpilo.babymonitor.settings.model.settings.DeviceId
import kotlin.coroutines.CoroutineContext
import kotlin.system.exitProcess

private const val TAG = "Relay"

private fun babyMonitorMain() {
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

    val configuration = loadConfigurationOrExit()

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
                name = configuration.host,
                relayHost = configuration.host,
            )
        relay.start(device, deriveRelayAccessKey(configuration.passphrase))
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

private fun loadConfigurationOrExit(): RelayConfiguration =
    when (val outcome = loadRelayConfiguration()) {
        is RelayConfigurationOutcome.Loaded -> {
            outcome.configuration
        }

        is RelayConfigurationOutcome.TemplateCreated -> {
            println("The Baby Monitor relay is not configured yet.")
            println("A blank configuration was created at ${outcome.path.path}.")
            println("Fill in the hostname and passphrase, then start the relay again.")
            exitProcess(1)
        }

        is RelayConfigurationOutcome.Incomplete -> {
            println("The Baby Monitor relay configuration at ${outcome.path.path} is incomplete.")
            println("These entries still need a value: ${outcome.blankKeys.joinToString(", ")}.")
            exitProcess(1)
        }
    }

@Suppress("unused")
fun main(args: Array<String>) {
    babyMonitorMain()
}

@Suppress("MemberNameEqualsClassName")
class Main private constructor() {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) = babyMonitorMain()
    }
}
