package org.vpilo.babymonitor.network.security.pairing

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.network.model.pairing.PairedDevice
import org.vpilo.babymonitor.network.model.repository.PairingStorageRepository
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.repository.SettingsRepository
import org.vpilo.babymonitor.settings.model.settings.LegacyPairedClientsJson
import org.vpilo.babymonitor.settings.model.settings.LegacyPairedServersJson
import org.vpilo.babymonitor.settings.model.settings.PairedDevicesJson
import java.util.concurrent.atomic.AtomicBoolean

internal class DefaultPairingStorageRepository(
    private val settingsRepository: SettingsRepository,
) : PairingStorageRepository {
    private val legacyPairingsCleared = AtomicBoolean(false)

    override val pairedDevices =
        settingsRepository
            .flowOf(Setting.PairedDevicesJson)
            .onStart { clearLegacyPairingsOnce() }
            .map { it.decodeOrEmpty() }

    override suspend fun pair(device: PairedDevice) {
        save(getDevices().filterNot { it.deviceId == device.deviceId } + device)
        Logger.i(TAG) { "Paired device ${device.deviceId}" }
    }

    override suspend fun find(deviceId: DeviceId): PairedDevice? = getDevices().firstOrNull { it.deviceId == deviceId.toString() }

    override suspend fun unpair(deviceId: DeviceId) {
        save(getDevices().filterNot { it.deviceId == deviceId.toString() })
        Logger.i(TAG) { "Unpaired device $deviceId" }
    }

    override suspend fun updateName(
        deviceId: DeviceId,
        newName: String,
    ) {
        val id = deviceId.toString()
        save(getDevices().map { if (it.deviceId == id) it.copy(name = newName) else it })
        Logger.i(TAG) { "Updated stored name for paired device $id" }
    }

    private suspend fun getDevices(): List<PairedDevice> = pairedDevices.first()

    private suspend fun save(devices: List<PairedDevice>) = settingsRepository.save(Setting.PairedDevicesJson, json.encodeToString(devices))

    private suspend fun clearLegacyPairingsOnce() {
        if (!legacyPairingsCleared.compareAndSet(false, true)) return
        settingsRepository.clear(Setting.LegacyPairedServersJson)
        settingsRepository.clear(Setting.LegacyPairedClientsJson)
    }

    private fun String.decodeOrEmpty(): List<PairedDevice> =
        runCatching { json.decodeFromString<List<PairedDevice>>(this) }
            .onFailure { Logger.w(TAG, it) { "Failed to decode paired devices, resetting to empty" } }
            .getOrDefault(emptyList())

    private companion object {
        private val TAG = DefaultPairingStorageRepository::class
        private val json =
            Json {
                ignoreUnknownKeys = true
                @OptIn(ExperimentalSerializationApi::class)
                exceptionsWithDebugInfo = false
            }
    }
}
