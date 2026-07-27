package org.vpilo.babymonitor.network.model.settings

import babymonitor.network.model.generated.resources.Res
import babymonitor.network.model.generated.resources.setting_relay_host_description
import babymonitor.network.model.generated.resources.setting_relay_host_title
import babymonitor.network.model.generated.resources.setting_relay_passphrase_description
import babymonitor.network.model.generated.resources.setting_relay_passphrase_title
import org.vpilo.babymonitor.model.settings.SettingId
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.makeSetting

val Setting.Companion.RelayHost: Setting<String> by makeSetting {
    Setting.makeString(
        id = SettingId("relay_host"),
        name = Res.string.setting_relay_host_title,
        description = Res.string.setting_relay_host_description,
        default = "",
        validateChange = { it.trim() },
    )
}

val Setting.Companion.RelayPassphrase: Setting<String> by makeSetting {
    Setting.makeString(
        id = SettingId("relay_passphrase"),
        name = Res.string.setting_relay_passphrase_title,
        description = Res.string.setting_relay_passphrase_description,
        default = "",
        validateChange = { it.trim() },
    )
}
