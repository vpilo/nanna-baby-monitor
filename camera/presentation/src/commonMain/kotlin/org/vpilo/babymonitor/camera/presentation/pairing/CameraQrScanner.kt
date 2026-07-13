package org.vpilo.babymonitor.camera.presentation.pairing

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Platform-specific camera viewfinder to capture pairing QR codes.
 */
@Composable
expect fun CameraQrScanner(
    modifier: Modifier,
    onPinEntered: (pin: String, deviceId: String) -> Unit,
)
