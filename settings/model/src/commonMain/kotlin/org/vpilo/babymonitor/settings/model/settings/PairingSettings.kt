package org.vpilo.babymonitor.settings.model.settings

import org.vpilo.babymonitor.model.settings.SettingId
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.makeSetting

// JSON-encoded List<PairedDevice> - internal storage, not a user-facing setting.
val Setting.Companion.PairedDevicesJson by makeSetting {
    Setting.makeString(
        id = SettingId("paired_devices_json"),
        default = "[]",
    )
}
