package org.vpilo.babymonitor.app.client.home

import org.vpilo.babymonitor.model.CaptureMode
import org.vpilo.babymonitor.model.repository.ConnectionState
import org.vpilo.babymonitor.model.repository.DEVICE_STATE_DATA_UNAVAILABLE

data class ClientHomeScreenState(
    val connectionState: ConnectionState = ConnectionState.Disconnected(ConnectionState.ErrorReason.NotConnectedYet),
    val captureMode: CaptureMode = CaptureMode.AUDIO_AND_VIDEO,
    val batteryLevel: Int = DEVICE_STATE_DATA_UNAVAILABLE,
    val signalQuality: Int = DEVICE_STATE_DATA_UNAVAILABLE,
    val isAudioPlaying: Boolean = false,
    val isVideoPlaying: Boolean = false,
)
