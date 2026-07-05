package org.vpilo.babymonitor.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import babymonitor.appcommon.generated.resources.Res
import babymonitor.appcommon.generated.resources.app_icon
import org.jetbrains.compose.resources.painterResource
import org.vpilo.babymonitor.settings.model.repository.SettingsRepository

private fun babyMonitorMain() {
    val koin = initializeKoin()
    val settingsRepository = koin.get<SettingsRepository>()

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Baby Monitor",
            icon = painterResource(Res.drawable.app_icon),
            state = rememberPersistedWindowState(settingsRepository),
        ) {
            CompositionLocalProvider(LocalQuitApplication provides ::exitApplication) {
                App()
            }
        }
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
