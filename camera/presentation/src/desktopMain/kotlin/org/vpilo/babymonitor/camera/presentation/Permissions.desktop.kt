package org.vpilo.babymonitor.camera.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun hasCameraPermission(): Boolean = true

@Composable
actual fun RequestCameraPermission(onGranted: () -> Unit, onDenied: () -> Unit) {
    remember {
        onGranted()
    }
}

@Composable
actual fun hasMicrophonePermission(): Boolean = true

@Composable
actual fun RequestMicrophonePermission(onGranted: () -> Unit, onDenied: () -> Unit) {
    remember {
        onGranted()
    }
}
