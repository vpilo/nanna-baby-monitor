package org.vpilo.babymonitor.model.repository

import org.vpilo.babymonitor.model.CaptureMode

data class ServerState(
    val isAvailable: Boolean,
    val captureMode: CaptureMode,
)
