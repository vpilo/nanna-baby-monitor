package org.vpilo.babymonitor.network.common.discovery

import kotlinx.coroutines.flow.Flow
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.network.model.repository.LocalDiscoveryRepository

internal expect class DefaultLocalDiscoveryRepository() : LocalDiscoveryRepository {
    override val discoveredDevicesFlow: Flow<Set<Device>>

    override val isRegisteredFlow: Flow<Boolean>

    override fun register(device: Device)

    override fun unregister()

    override fun refresh()
}
