package org.vpilo.babymonitor.app.menu

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.runtime.Composable
import babymonitor.appcommon.generated.resources.Res
import babymonitor.appcommon.generated.resources.high_quality
import babymonitor.appcommon.generated.resources.menu_change_role_description
import babymonitor.appcommon.generated.resources.menu_change_role_title
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.vpilo.babymonitor.app.navigation.Route
import org.vpilo.babymonitor.camera.model.settings.CameraResolution
import org.vpilo.babymonitor.settings.model.PlatformAvailability
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.isSupportedOnCurrentPlatform
import org.vpilo.babymonitor.settings.presentation.composables.MenuItem
import org.vpilo.babymonitor.settings.presentation.composables.MenuSettingEnumItem

@Composable
fun AppMenuContents(onNavigateTo: (Route) -> Unit) {
    MenuItem(
        imageVector = Icons.Default.SwapHoriz,
        title = stringResource(Res.string.menu_change_role_title),
        description = stringResource(Res.string.menu_change_role_description),
        onClick = {
            onNavigateTo(Route.AppRoleChooser)
        },
    )

    MenuSettingEnumItem(
        setting = Setting.CameraResolution,
        imageVector = vectorResource(Res.drawable.high_quality),
    )

    if (PlatformAvailability.DesktopOnly.isSupportedOnCurrentPlatform) {
        MenuItem(
            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
            title = "Quit",
            onClick = {
                onNavigateTo(Route.Quit)
            },
        )
    }
}
