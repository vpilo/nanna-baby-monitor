package org.vpilo.babymonitor.settings.model.repository

import org.vpilo.babymonitor.model.NetworkAddress
import org.vpilo.babymonitor.model.repository.DeviceId

interface NetworkAddressCacheRepository {
    suspend fun get(id: DeviceId): Set<NetworkAddress>

    suspend fun put(id: DeviceId, addresses: Set<NetworkAddress>)
}
