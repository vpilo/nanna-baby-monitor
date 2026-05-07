package org.vpilo.babymonitor.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import babymonitor.appcommon.generated.resources.Res
import babymonitor.appcommon.generated.resources.app_icon
import org.jetbrains.compose.resources.painterResource

fun main() {
    initializeKoin()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Baby Monitor",
            icon = painterResource(Res.drawable.app_icon),
        ) {
            CompositionLocalProvider(LocalQuitApplication provides ::exitApplication) {
                App()
            }
        }
    }
}
