package org.vpilo.babymonitor.settings.model.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.repository.SettingsRepository
import org.vpilo.babymonitor.settings.model.settings.DeviceId
import org.vpilo.babymonitor.settings.model.settings.DeviceName

class GetLocalClientDeviceFlowUseCase(
    private val settings: SettingsRepository,
) {
    operator fun invoke(): Flow<Device.Client> =
        combine(
            settings.flowOf(Setting.DeviceId),
            settings.flowOf(Setting.DeviceName),
        ) { rawId, deviceName ->
            val id = checkNotNull(DeviceId.parseOrNull(rawId)) { "Invalid device ID: $rawId" }
            Device.Client(id = id, name = deviceName)
        }
}
