package org.vpilo.babymonitor.app.settings.desktop

import org.vpilo.babymonitor.model.settings.SettingId
import org.vpilo.babymonitor.settings.model.PlatformAvailability
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.makeSetting

val Setting.Companion.WindowSize by makeSetting {
    Setting.makeString(
        id = SettingId("window_size"),
        platform = PlatformAvailability.DesktopOnly,
        default = "",
    )
}

val Setting.Companion.WindowPlacement by makeSetting {
    Setting.makeString(
        id = SettingId("window_placement"),
        platform = PlatformAvailability.DesktopOnly,
        default = "",
    )
}

val Setting.Companion.WindowPosition by makeSetting {
    Setting.makeString(
        id = SettingId("window_position"),
        platform = PlatformAvailability.DesktopOnly,
        default = "",
    )
}
