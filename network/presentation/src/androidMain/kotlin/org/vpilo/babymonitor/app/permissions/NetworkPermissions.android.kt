package org.vpilo.babymonitor.app.permissions

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

// This permission is required for LAN discovery on SDK 37+ for LAN discovery:
// https://developer.android.com/privacy-and-security/local-network-permission
@Composable
actual fun hasLocalNetworkPermission(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.CINNAMON_BUN) return true
    return ContextCompat.checkSelfPermission(LocalContext.current, Manifest.permission.ACCESS_LOCAL_NETWORK) ==
        PackageManager.PERMISSION_GRANTED
}

@Composable
actual fun RequestLocalNetworkPermission(
    onGranted: () -> Unit,
    onDenied: () -> Unit,
) {
    if (hasLocalNetworkPermission()) {
        LaunchedEffect(Unit) { onGranted() }
        return
    }

    val launcher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { isGranted -> if (isGranted) onGranted() else onDenied() }

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.CINNAMON_BUN) return
    LaunchedEffect(Unit) {
        launcher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
    }
}
