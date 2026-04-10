package org.vpilo.babymonitor.data.settings

import org.vpilo.babymonitor.model.AppRole
import org.vpilo.babymonitor.model.settings.SettingId
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.makeSetting

val Setting.Companion.AppRole by makeSetting {
    Setting.makeEnum(
        id = SettingId("selected_app_role"),
        default = AppRole.UNDECIDED,
        values = emptyMap(),
    )
}
