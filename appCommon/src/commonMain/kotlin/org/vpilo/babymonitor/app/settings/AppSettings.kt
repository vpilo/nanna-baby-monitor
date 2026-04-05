package org.vpilo.babymonitor.app.settings

import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.makeSetting

val Setting.Companion.IsFirstRun by makeSetting {
    Setting(
        id = "is_app_first_run",
        type = Boolean::class,
        default = true,
    )
}
