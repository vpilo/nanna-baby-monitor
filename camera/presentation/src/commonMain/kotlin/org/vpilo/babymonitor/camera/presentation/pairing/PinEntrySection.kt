package org.vpilo.babymonitor.camera.presentation.pairing

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Platform-specific pairing PIN input. Desktop shows a manual text field (the design's "type the PIN off
 * the screen" path); Android (Task 17) scans the QR code with the camera instead and extracts the PIN from
 * it, additionally checking the QR's encoded device ID against [expectedDeviceId] before calling
 * [onPinEntered] — desktop has no such check since the user already selected the target server explicitly.
 */
@Composable
expect fun PinEntrySection(
    modifier: Modifier,
    expectedDeviceId: String,
    onPinEntered: (pin: String) -> Unit,
)
