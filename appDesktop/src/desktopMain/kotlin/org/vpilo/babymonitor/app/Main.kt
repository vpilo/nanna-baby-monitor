package org.vpilo.babymonitor.app

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() {
    initializeKoin()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Baby Monitor",
        ) {
            App()
        }
    }
}
