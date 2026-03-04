package org.vpilo.babymonitor.camera.presentation

import androidx.compose.runtime.Composable

/**
 * Returns whether the application has permission to access the device's camera.
 */
@Composable
expect fun hasCameraPermission(): Boolean

/**
 * Requests user permission to use the device's camera, and calls [onGranted] if permission is granted, or [onDenied] if it is denied.
 */
@Composable
expect fun RequestCameraPermission(onGranted: () -> Unit, onDenied: () -> Unit)

/**
 * Returns whether the application has permission to access the device's microphone.
 */
@Composable
expect fun hasMicrophonePermission(): Boolean

/**
 * Requests user permission to use the device's microphone, and calls [onGranted] if permission is granted, or [onDenied] if it is denied.
 */
@Composable
expect fun RequestMicrophonePermission(onGranted: () -> Unit, onDenied: () -> Unit)
