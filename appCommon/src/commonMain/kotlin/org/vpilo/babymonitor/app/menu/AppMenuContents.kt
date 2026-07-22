package org.vpilo.babymonitor.app.menu

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.NoiseAware
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import babymonitor.appcommon.generated.resources.Res
import babymonitor.appcommon.generated.resources.disconnect
import babymonitor.appcommon.generated.resources.high_quality
import babymonitor.appcommon.generated.resources.menu_change_role_description
import babymonitor.appcommon.generated.resources.menu_change_role_title
import babymonitor.appcommon.generated.resources.menu_disconnect_description
import babymonitor.appcommon.generated.resources.menu_disconnect_title
import babymonitor.appcommon.generated.resources.menu_paired_devices_description
import babymonitor.appcommon.generated.resources.menu_paired_devices_title
import babymonitor.appcommon.generated.resources.menu_quit_android
import babymonitor.appcommon.generated.resources.menu_quit_desktop
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.core.module.dsl.viewModelOf
import org.vpilo.babymonitor.app.navigation.Route
import org.vpilo.babymonitor.app.settings.RelayHost
import org.vpilo.babymonitor.camera.model.settings.CameraResolution
import org.vpilo.babymonitor.camera.model.settings.LowLightBoost
import org.vpilo.babymonitor.camera.model.settings.SilenceDetectionThreshold
import org.vpilo.babymonitor.model.AppRole
import org.vpilo.babymonitor.presentation.AppPreviewTheme
import org.vpilo.babymonitor.settings.model.PlatformAvailability
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.isSupportedOnCurrentPlatform
import org.vpilo.babymonitor.settings.model.settings.DeviceName
import org.vpilo.babymonitor.settings.presentation.composables.MenuItem
import org.vpilo.babymonitor.settings.presentation.composables.MenuSettingEnumItem
import org.vpilo.babymonitor.settings.presentation.composables.MenuSettingItem
import org.vpilo.babymonitor.settings.presentation.composables.MenuSettingItemViewModel

@Composable
fun ColumnScope.AppMenuContents(
    currentRole: AppRole,
    showDisconnect: Boolean,
    onNavigateTo: (Route, popUpTo: Route?) -> Unit,
    onNavigateToRoot: () -> Unit,
    onDisconnect: () -> Unit,
) {
    if (currentRole == AppRole.CLIENT && showDisconnect) {
        MenuItem(
            imageVector = vectorResource(Res.drawable.disconnect),
            title = stringResource(Res.string.menu_disconnect_title),
            description = stringResource(Res.string.menu_disconnect_description),
            onClick = onDisconnect,
        )
    }

    MenuItem(
        imageVector = Icons.Default.Devices,
        title = stringResource(Res.string.menu_paired_devices_title),
        description = stringResource(Res.string.menu_paired_devices_description),
        onClick = { onNavigateTo(Route.PairedDevices, null) },
    )

    if (currentRole == AppRole.SERVER) {
        MenuSettingEnumItem(
            setting = Setting.CameraResolution,
            imageVector = vectorResource(Res.drawable.high_quality),
        )
        MenuSettingItem(
            setting = Setting.LowLightBoost,
            imageVector = Icons.Default.BrightnessMedium,
        )
        MenuSettingItem(
            setting = Setting.SilenceDetectionThreshold,
            imageVector = Icons.Default.NoiseAware,
        )
    }

    MenuSettingItem(
        setting = Setting.DeviceName,
        imageVector = Icons.Default.Person,
    )

    MenuSettingItem(
        setting = Setting.RelayHost,
        imageVector = Icons.Default.Cloud,
    )

    MenuItem(
        imageVector = Icons.Default.SwapHoriz,
        title = stringResource(Res.string.menu_change_role_title),
        description = stringResource(Res.string.menu_change_role_description),
        onClick = onNavigateToRoot,
    )

    val quitLabel =
        when {
            PlatformAvailability.DesktopOnly.isSupportedOnCurrentPlatform -> Res.string.menu_quit_desktop
            PlatformAvailability.AndroidOnly.isSupportedOnCurrentPlatform -> Res.string.menu_quit_android
            else -> error("Unsupported platform")
        }
    MenuItem(
        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
        title = stringResource(quitLabel),
        onClick = {
            onNavigateTo(Route.Quit, null)
        },
    )
}

@Preview
@Composable
private fun AppMenuContentsServerPreview() =
    AppPreviewTheme(
        withModule = {
            viewModelOf(::MenuSettingItemViewModel)
        },
    ) {
        Column {
            AppMenuContents(
                currentRole = AppRole.SERVER,
                showDisconnect = true,
                onNavigateTo = { _, _ -> },
                onNavigateToRoot = {},
                onDisconnect = {},
            )
        }
    }

@Preview
@Composable
private fun AppMenuContentsClientPreview() =
    AppPreviewTheme(
        withModule = {
            viewModelOf(::MenuSettingItemViewModel)
        },
    ) {
        Column {
            AppMenuContents(
                currentRole = AppRole.CLIENT,
                showDisconnect = true,
                onNavigateTo = { _, _ -> },
                onNavigateToRoot = {},
                onDisconnect = {},
            )
        }
    }
