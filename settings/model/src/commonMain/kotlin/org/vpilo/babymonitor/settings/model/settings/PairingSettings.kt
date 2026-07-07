package org.vpilo.babymonitor.settings.model.settings

import org.vpilo.babymonitor.model.settings.SettingId
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.makeSetting

// JSON-encoded List<PairedServer> / List<PairedClient> — internal storage, not user-facing settings.
val Setting.Companion.PairedServersJson by makeSetting {
    Setting.makeString(
        id = SettingId("paired_servers_json"),
        default = "[]",
    )
}

val Setting.Companion.PairedClientsJson by makeSetting {
    Setting.makeString(
        id = SettingId("paired_clients_json"),
        default = "[]",
    )
}
