package org.vpilo.babymonitor.settings.model.settings

import babymonitor.settings.model.generated.resources.Res
import babymonitor.settings.model.generated.resources.default_device_name
import babymonitor.settings.model.generated.resources.settings_device_name_description
import babymonitor.settings.model.generated.resources.settings_device_name_title
import org.jetbrains.compose.resources.getString
import org.vpilo.babymonitor.model.settings.SettingId
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.makeSetting

val Setting.Companion.DeviceId by makeSetting {
    Setting.makeString(
        id = SettingId("device_id"),
        default = "",
    )
}

val Setting.Companion.DeviceName by makeSetting {
    Setting.makeString(
        id = SettingId("device_name"),
        name = Res.string.settings_device_name_title,
        description = Res.string.settings_device_name_description,
        default = "",
        validateChange = { it.replace(deviceNameFilter, " ").trim().ifBlank { makeDefaultDeviceName() } },
    )
}

private suspend fun makeDefaultDeviceName(): String {
    val appName = getString(Res.string.default_device_name)
    val randomId = (1000..9999).random()
    return "$appName $randomId"
}

private val deviceNameFilter: Regex by lazy { Regex("[\\s\\c ]+") }
