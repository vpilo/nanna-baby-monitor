package org.vpilo.babymonitor.app.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun hasLocalNetworkPermission(): Boolean = true

@Composable
actual fun RequestLocalNetworkPermission(
    onGranted: () -> Unit,
    onDenied: () -> Unit,
) {
    remember {
        onGranted()
    }
}
