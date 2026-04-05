package org.vpilo.babymonitor.app.settings

import org.vpilo.babymonitor.model.CaptureMode
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.makeSetting

val Setting.Companion.IsFirstRun by makeSetting {
    Setting(
        id = "is_app_first_run",
        type = Boolean::class,
        default = true,
    )
}

val Setting.Companion.LastCaptureMode by makeSetting {
    Setting(
        id = "last_capture_mode",
        type = CaptureMode::class,
        default = CaptureMode.AUDIO_AND_VIDEO,
    )
}
