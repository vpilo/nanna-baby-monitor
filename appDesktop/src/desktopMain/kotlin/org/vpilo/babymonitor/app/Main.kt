package org.vpilo.babymonitor.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.LocalWindowExceptionHandlerFactory
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowExceptionHandler
import androidx.compose.ui.window.WindowExceptionHandlerFactory
import androidx.compose.ui.window.application
import babymonitor.appcommon.generated.resources.Res
import babymonitor.appcommon.generated.resources.app_icon
import babymonitor.appcommon.generated.resources.app_name
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.vpilo.babymonitor.settings.model.repository.SettingsRepository
import kotlin.system.exitProcess

@OptIn(ExperimentalComposeUiApi::class)
private val exceptionHandlerFactory =
    WindowExceptionHandlerFactory {
        WindowExceptionHandler { throwable ->
            Thread
                .getDefaultUncaughtExceptionHandler()
                ?.uncaughtException(Thread.currentThread(), throwable)
            exitProcess(1)
        }
    }

private fun babyMonitorMain() {
    val koin = initializeKoin()
    val settingsRepository = koin.get<SettingsRepository>()

    onApplicationStart()
    application {
        fun onCloseRequestHandler() {
            onApplicationStop()
            exitApplication()
        }

        @OptIn(ExperimentalComposeUiApi::class)
        CompositionLocalProvider(LocalWindowExceptionHandlerFactory provides exceptionHandlerFactory) {
            Window(
                onCloseRequest = ::onCloseRequestHandler,
                title = stringResource(Res.string.app_name),
                icon = painterResource(Res.drawable.app_icon),
                state = rememberPersistedWindowState(settingsRepository),
            ) {
                CompositionLocalProvider(LocalQuitApplication provides ::exitApplication) {
                    App()
                }
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
