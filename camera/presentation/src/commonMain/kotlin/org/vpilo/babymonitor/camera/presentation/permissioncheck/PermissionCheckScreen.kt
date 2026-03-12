package org.vpilo.babymonitor.camera.presentation.permissioncheck

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import babymonitor.camera.presentation.generated.resources.Res
import babymonitor.camera.presentation.generated.resources.permissions_needed
import org.jetbrains.compose.resources.stringResource
import org.vpilo.babymonitor.camera.presentation.RequestCameraPermission
import org.vpilo.babymonitor.camera.presentation.RequestMicrophonePermission
import org.vpilo.babymonitor.camera.presentation.hasCameraPermission
import org.vpilo.babymonitor.camera.presentation.hasMicrophonePermission
import org.vpilo.babymonitor.presentation.MaxUserInterfaceWidth
import org.vpilo.babymonitor.presentation.Theme
import org.vpilo.babymonitor.presentation.composables.LoadingBox

@Composable
fun PermissionCheckScreen(
    modifier: Modifier = Modifier,
    onAllPermissionsGranted: () -> Unit,
) {
    val deniedPermissions = remember { mutableIntStateOf(2) }

    when {
        !hasCameraPermission() ->
            RequestCameraPermission(
                onGranted = { deniedPermissions.intValue-- },
                onDenied = {},
            )

        !hasMicrophonePermission() ->
            RequestMicrophonePermission(
                onGranted = { deniedPermissions.intValue-- },
                onDenied = {},
            )

        else -> {
            onAllPermissionsGranted()
            return
        }
    }

    if (deniedPermissions.intValue == 0) {
        return
    }

    Surface(
        color = Theme.Colors.mainBackground,
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Column(
            modifier = modifier
                .widthIn(max = MaxUserInterfaceWidth)
                .fillMaxSize()
                .padding(Theme.Paddings.Medium),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.permissions_needed),
                style = MaterialTheme.typography.titleMedium,
                color = Theme.Colors.error,
            )
            LoadingBox()
        }
    }
}
