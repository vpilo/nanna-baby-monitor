package org.vpilo.babymonitor.app.client.home

import org.vpilo.babymonitor.model.CaptureMode
import org.vpilo.babymonitor.model.repository.DEVICE_STATE_DATA_UNAVAILABLE
import org.vpilo.babymonitor.model.repository.NetworkState

data class ClientHomeScreenState(
    val networkState: NetworkState = NetworkState.Disconnected(NetworkState.ErrorReason.NotConnectedYet),
    val captureMode: CaptureMode = CaptureMode.AUDIO_AND_VIDEO,
    val batteryLevel: Int = DEVICE_STATE_DATA_UNAVAILABLE,
    val signalQuality: Int = DEVICE_STATE_DATA_UNAVAILABLE,
    val isAudioPlaying: Boolean = false,
    val isVideoPlaying: Boolean = false,
)
