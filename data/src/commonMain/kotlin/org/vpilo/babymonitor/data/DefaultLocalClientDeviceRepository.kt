package org.vpilo.babymonitor.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.model.repository.LocalClientDeviceRepository
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.repository.SettingsRepository
import org.vpilo.babymonitor.settings.model.settings.DeviceId
import org.vpilo.babymonitor.settings.model.settings.DeviceName

internal class DefaultLocalClientDeviceRepository(
    settings: SettingsRepository,
) : LocalClientDeviceRepository {
    override val localDevice: Flow<Device.Client> =
        combine(
            settings.flowOf(Setting.DeviceId),
            settings.flowOf(Setting.DeviceName),
        ) { rawId, deviceName ->
            val id = checkNotNull(DeviceId.parseOrNull(rawId)) { "Invalid device ID: $rawId" }
            Device.Client(id = id, name = deviceName)
        }
}
