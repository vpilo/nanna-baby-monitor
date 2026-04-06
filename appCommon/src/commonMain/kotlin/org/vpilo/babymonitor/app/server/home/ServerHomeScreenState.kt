package org.vpilo.babymonitor.app.server.home

import org.vpilo.babymonitor.model.CaptureMode

data class ServerHomeScreenState(
    val isAvailable: Boolean = false,
    val captureMode: CaptureMode = CaptureMode.AUDIO_AND_VIDEO,
)
