package org.vpilo.babymonitor.model.repository

import org.vpilo.babymonitor.model.CaptureMode

data class ServerState(
    val isAvailableOnLocalNetwork: Boolean = false,
    val isAvailableOnRelay: Boolean = false,
    val captureMode: CaptureMode = CaptureMode.AUDIO_AND_VIDEO,
    val batteryLevel: Int = 100,
    val signalQuality: Int = 100,
) {
    val isAvailable: Boolean
        get() = isAvailableOnLocalNetwork || isAvailableOnRelay
}
