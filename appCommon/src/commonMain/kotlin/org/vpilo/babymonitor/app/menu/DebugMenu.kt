package org.vpilo.babymonitor.app.menu

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import org.koin.core.module.dsl.viewModelOf
import org.vpilo.babymonitor.common.BuildInfo
import org.vpilo.babymonitor.presentation.AppPreviewTheme
import org.vpilo.babymonitor.presentation.Theme
import org.vpilo.babymonitor.settings.presentation.composables.MenuItem
import org.vpilo.babymonitor.settings.presentation.composables.MenuSettingItemViewModel

@Composable
internal fun ColumnScope.DebugMenu(modifier: Modifier = Modifier) {
    if (!BuildInfo.IS_DEBUG) {
        return
    }

    var shouldCrashFromCoroutine by remember { mutableStateOf(false) }
    var shouldCrashMainThread by remember { mutableStateOf(false) }

    Spacer(modifier = modifier.height(Theme.Paddings.Large))
    HorizontalDivider(modifier = modifier.padding(Theme.Paddings.Medium))

    MenuItem(
        imageVector = Icons.Default.BugReport,
        title = "Crash from a coroutine",
        onClick = {
            shouldCrashFromCoroutine = true
        },
    )
    MenuItem(
        imageVector = Icons.Default.BugReport,
        title = "Crash from main thread",
        onClick = {
            shouldCrashMainThread = true
        },
    )

    LaunchedEffect(shouldCrashFromCoroutine) {
        if (shouldCrashFromCoroutine) {
            @Suppress("CoroutineContextWithJob")
            withContext(SupervisorJob() + Dispatchers.Default) {
                error("ciao io ero una coroutine")
            }
        }
    }
    LaunchedEffect(shouldCrashMainThread) {
        if (shouldCrashMainThread) {
            error("una volta io ero main")
        }
    }
}

@Preview
@Composable
private fun DebugMenuPreview() =
    AppPreviewTheme(
        withModule = {
            viewModelOf(::MenuSettingItemViewModel)
        },
    ) {
        Column {
            DebugMenu()
        }
    }
