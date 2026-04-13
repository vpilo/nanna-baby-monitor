package org.vpilo.babymonitor.settings.model.settings

import org.vpilo.babymonitor.model.settings.SettingId
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.makeSetting

val Setting.Companion.DeviceName by makeSetting {
    Setting.makePrimitive(
        id = SettingId("device_name"),
        default = "",
    )
}
