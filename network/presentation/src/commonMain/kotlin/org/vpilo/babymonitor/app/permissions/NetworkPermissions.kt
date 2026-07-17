package org.vpilo.babymonitor.app.permissions

import androidx.compose.runtime.Composable

/**
 * Returns whether the application has permission to access devices in the local network.
 */
@Composable
expect fun hasLocalNetworkPermission(): Boolean

/**
 * Requests user permission to access devices in the local network, and calls [onGranted] if permission is granted, or [onDenied] if
 * it is denied.
 */
@Composable
expect fun RequestLocalNetworkPermission(
    onGranted: () -> Unit,
    onDenied: () -> Unit,
)
