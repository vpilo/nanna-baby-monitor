package org.vpilo.babymonitor.network.model.repository

import kotlinx.coroutines.flow.Flow
import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.network.model.pairing.PairedDevice

interface PairingStorageRepository {
    val pairedDevices: Flow<List<PairedDevice>>

    suspend fun pair(device: PairedDevice)

    suspend fun find(deviceId: DeviceId): PairedDevice?

    suspend fun unpair(deviceId: DeviceId)

    suspend fun updateName(
        deviceId: DeviceId,
        newName: String,
    )
}
