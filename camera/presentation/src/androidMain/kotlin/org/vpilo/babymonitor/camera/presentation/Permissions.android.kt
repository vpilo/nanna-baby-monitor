package org.vpilo.babymonitor.camera.presentation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
actual fun hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(LocalContext.current, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

@Composable
actual fun RequestCameraPermission(onGranted: () -> Unit, onDenied: () -> Unit) {
    if (hasCameraPermission()) {
        LaunchedEffect(Unit) { onGranted() }
    } else {
        val launcher =
            rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { isGranted -> if (isGranted) onGranted() else onDenied() }

        LaunchedEffect(Unit) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }
}

@Composable
actual fun hasMicrophonePermission(): Boolean =
    ContextCompat.checkSelfPermission(LocalContext.current, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

@Composable
actual fun RequestMicrophonePermission(onGranted: () -> Unit, onDenied: () -> Unit) {
    if (hasMicrophonePermission()) {
        LaunchedEffect(Unit) { onGranted() }
    } else {
        val launcher =
            rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { isGranted -> if (isGranted) onGranted() else onDenied() }

        LaunchedEffect(Unit) {
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}
