package org.vpilo.babymonitor.app.server.home

import org.vpilo.babymonitor.model.CaptureMode

data class ServerHomeScreenState(
    val isAvailableOnLocalNetwork: Boolean = false,
    val isAvailableOnRelay: Boolean = false,
    val isRelayConfigured: Boolean = false,
    val captureMode: CaptureMode = CaptureMode.AUDIO_AND_VIDEO,
    val name: String = "Server",
) {
    override fun toString(): String =
        "ServerHomeScreenState(" +
            "onLocalNetwork=$isAvailableOnLocalNetwork, " +
            "onRelay=$isAvailableOnRelay, " +
            "hasRelay=$isRelayConfigured, " +
            "captureMode=$captureMode" +
            ")"
}
