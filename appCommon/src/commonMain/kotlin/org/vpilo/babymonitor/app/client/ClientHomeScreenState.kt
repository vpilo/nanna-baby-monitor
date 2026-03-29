package org.vpilo.babymonitor.app.client

import org.vpilo.babymonitor.model.CaptureMode
import org.vpilo.babymonitor.model.repository.NetworkState

data class ClientHomeScreenState(
    val networkState: NetworkState = NetworkState.Disconnected(NetworkState.ErrorReason.NotConnectedYet),
    val captureMode: CaptureMode = CaptureMode.AUDIO_AND_VIDEO,
    val isAudioPlaying: Boolean = false,
)
