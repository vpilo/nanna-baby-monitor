package org.vpilo.babymonitor.data.settings

import org.vpilo.babymonitor.model.AppRole
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.makeSetting

val Setting.Companion.AppRole by makeSetting {
    Setting(
        id = "test",
        type = AppRole::class,
        default = AppRole.UNDECIDED,
    )
}

