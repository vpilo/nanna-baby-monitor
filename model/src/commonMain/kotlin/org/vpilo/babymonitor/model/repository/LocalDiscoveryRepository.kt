package org.vpilo.babymonitor.model.repository

import kotlinx.coroutines.flow.Flow
import org.vpilo.babymonitor.model.Device

interface LocalDiscoveryRepository {
    val discoveredDevicesFlow: Flow<Set<Device>>

    fun register(device: Device)

    fun unregister()

    fun refresh()
}
