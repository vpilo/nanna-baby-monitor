package org.vpilo.babymonitor.app.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun hasNotificationsPermission(): Boolean = true

@Composable
actual fun RequestNotificationsPermission(
    onGranted: () -> Unit,
    onDenied: () -> Unit,
) {
    remember {
        onGranted()
    }
}
