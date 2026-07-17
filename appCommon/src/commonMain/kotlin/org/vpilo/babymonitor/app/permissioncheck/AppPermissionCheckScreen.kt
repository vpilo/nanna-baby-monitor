package org.vpilo.babymonitor.app.permissioncheck

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import babymonitor.appcommon.generated.resources.Res
import babymonitor.appcommon.generated.resources.app_permissions_needed
import babymonitor.appcommon.generated.resources.navigation_title_app_permissions
import org.jetbrains.compose.resources.stringResource
import org.vpilo.babymonitor.app.permissions.RequestLocalNetworkPermission
import org.vpilo.babymonitor.app.permissions.RequestNotificationsPermission
import org.vpilo.babymonitor.app.permissions.hasLocalNetworkPermission
import org.vpilo.babymonitor.app.permissions.hasNotificationsPermission
import org.vpilo.babymonitor.presentation.composables.AppDestination
import org.vpilo.babymonitor.presentation.composables.AppDestinationMainAction
import org.vpilo.babymonitor.presentation.composables.LoadingBox

@Composable
fun AppPermissionCheckScreen(
    modifier: Modifier = Modifier,
    onAllPermissionsGranted: () -> Unit,
) {
    AppDestination(
        title = Res.string.navigation_title_app_permissions,
        mainAction = AppDestinationMainAction.None,
        onMainActionClicked = {},
    ) {
        val deniedPermissions = remember { mutableIntStateOf(2) }

        when {
            !hasNotificationsPermission() -> {
                RequestNotificationsPermission(
                    onGranted = { deniedPermissions.intValue-- },
                    onDenied = {},
                )
            }

            !hasLocalNetworkPermission() -> {
                RequestLocalNetworkPermission(
                    onGranted = { deniedPermissions.intValue-- },
                    onDenied = {},
                )
            }

            else -> {
                LaunchedEffect(Unit) {
                    onAllPermissionsGranted()
                }
                return@AppDestination
            }
        }

        if (deniedPermissions.intValue == 0) {
            return@AppDestination
        }

        Box(
            modifier =
                modifier
                    .fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(Res.string.app_permissions_needed),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                LoadingBox()
            }
        }
    }
}
