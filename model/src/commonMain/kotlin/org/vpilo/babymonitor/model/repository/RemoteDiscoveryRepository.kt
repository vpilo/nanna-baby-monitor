package org.vpilo.babymonitor.model.repository

import kotlinx.coroutines.flow.Flow
import org.vpilo.babymonitor.model.Device

interface RemoteDiscoveryRepository {
    val discoveredDevicesFlow: Flow<Set<Device>>

    fun setRelayHost(host: String = "")
}
