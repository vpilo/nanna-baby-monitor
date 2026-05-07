package org.vpilo.babymonitor.camera.presentation.permissioncheck

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import babymonitor.camera.presentation.generated.resources.Res
import babymonitor.camera.presentation.generated.resources.navigation_title_permissions
import babymonitor.camera.presentation.generated.resources.permissions_needed
import org.jetbrains.compose.resources.stringResource
import org.vpilo.babymonitor.camera.presentation.RequestCameraPermission
import org.vpilo.babymonitor.camera.presentation.RequestMicrophonePermission
import org.vpilo.babymonitor.camera.presentation.hasCameraPermission
import org.vpilo.babymonitor.camera.presentation.hasMicrophonePermission
import org.vpilo.babymonitor.presentation.composables.AppDestination
import org.vpilo.babymonitor.presentation.composables.LoadingBox

@Composable
fun CameraPermissionCheckScreen(
    modifier: Modifier = Modifier,
    onBackClicked: () -> Unit,
    onAllPermissionsGranted: () -> Unit,
) {
    AppDestination(
        title = Res.string.navigation_title_permissions,
        onMainActionClicked = onBackClicked,
    ) {
        val deniedPermissions = remember { mutableIntStateOf(2) }

        when {
            !hasCameraPermission() -> {
                RequestCameraPermission(
                    onGranted = { deniedPermissions.intValue-- },
                    onDenied = {},
                )
            }

            !hasMicrophonePermission() -> {
                RequestMicrophonePermission(
                    onGranted = { deniedPermissions.intValue-- },
                    onDenied = {},
                )
            }

            else -> {
                onAllPermissionsGranted()
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
                    text = stringResource(Res.string.permissions_needed),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                LoadingBox()
            }
        }
    }
}
