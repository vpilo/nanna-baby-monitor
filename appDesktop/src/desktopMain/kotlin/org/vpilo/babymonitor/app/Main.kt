package org.vpilo.babymonitor.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() {
    initializeKoin()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Baby Monitor",
        ) {
            CompositionLocalProvider(LocalQuitApplication provides ::exitApplication) {
                App()
            }
        }
    }
}
