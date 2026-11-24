package org.vpilo.babymonitor.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberDialogState
import babymonitor.appcommon.generated.resources.Res
import babymonitor.appcommon.generated.resources.app_icon
import babymonitor.appcommon.generated.resources.app_name
import babymonitor.appdesktop.generated.resources.app_already_running
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.vpilo.babymonitor.presentation.AppTheme
import org.vpilo.babymonitor.presentation.Theme
import java.awt.GraphicsEnvironment
import kotlin.time.Duration.Companion.milliseconds
import babymonitor.appdesktop.generated.resources.Res as DesktopRes

internal fun launchAlreadyRunningDialog() {
    if (GraphicsEnvironment.isHeadless()) {
        val message = runBlocking { getString(DesktopRes.string.app_already_running) }
        System.err.println(message)
        return
    }

    application {
        AppAlreadyRunningDialog(onExit = ::exitApplication)
    }
}

@Composable
private fun AppAlreadyRunningDialog(onExit: () -> Unit) {
    DialogWindow(
        onCloseRequest = onExit,
        state = rememberDialogState(size = DpSize.Unspecified),
        title = stringResource(Res.string.app_name),
        icon = painterResource(Res.drawable.app_icon),
        resizable = false,
    ) {
        // The dialog can be resized after initial composition, and doesn't properly recompose by itself.
        LaunchedEffect(window) {
            repeat(3) {
                delay(10.milliseconds)
                window.pack()
                delay(50.milliseconds)
            }
        }

        AppTheme {
            Column(
                modifier = Modifier.fillMaxSize().padding(Theme.Paddings.Larger),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Theme.Paddings.Medium),
            ) {
                Image(
                    modifier = Modifier.size(Theme.Sizes.IconLarge),
                    painter = painterResource(Res.drawable.app_icon),
                    contentDescription = null,
                )
                Text(stringResource(DesktopRes.string.app_already_running), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
