package org.vpilo.babymonitor.app.server

import org.vpilo.babymonitor.model.CaptureMode

sealed interface ServerHomeScreenAction {
    data class CaptureModeSelected(
        val captureMode: CaptureMode,
    ) : ServerHomeScreenAction
}
