package org.vpilo.babymonitor.network.model.repository

import kotlinx.coroutines.flow.Flow
import org.vpilo.babymonitor.model.Device

interface DiscoveredDeviceCacheRepository {
    val cachedDevicesFlow: Flow<Set<Device>>

    suspend fun set(devices: Set<Device>)
}
