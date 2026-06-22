package org.vpilo.babymonitor.network.common.discovery

import kotlinx.coroutines.flow.Flow
import org.vpilo.babymonitor.model.Device

expect class DiscoveryManager() {
    val discoveredDevicesFlow: Flow<Set<Device>>

    val discoveredDevices: Set<Device>

    val isActive: Boolean

    fun register(device: Device)

    fun unregister()

    fun refresh()
}
