package org.vpilo.babymonitor.app

import androidx.compose.runtime.Composable

/**
 * Returns whether the application has permission to post notifications.
 */
@Composable
expect fun hasNotificationsPermission(): Boolean

/**
 * Requests user permission to post notifications, and calls [onGranted] if permission is granted, or [onDenied] if it is denied.
 */
@Composable
expect fun RequestNotificationsPermission(
    onGranted: () -> Unit,
    onDenied: () -> Unit,
)
