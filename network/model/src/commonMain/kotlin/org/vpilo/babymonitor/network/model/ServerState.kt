package org.vpilo.babymonitor.network.model

import org.vpilo.babymonitor.model.CaptureMode

data class ServerState(
    // Unused in client network repository.
    val isAvailableOnLocalNetwork: Boolean = false,
    // Unused in client network repository.
    val isAvailableOnRelay: Boolean = false,
    val captureMode: CaptureMode = CaptureMode.AUDIO_AND_VIDEO,
    val batteryLevel: Int = 100,
    val signalQuality: Int = 100,
) {
    val isAvailable: Boolean
        get() = isAvailableOnLocalNetwork || isAvailableOnRelay
}
