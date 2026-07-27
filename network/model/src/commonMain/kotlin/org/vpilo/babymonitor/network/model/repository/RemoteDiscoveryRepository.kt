package org.vpilo.babymonitor.network.model.repository

import kotlinx.coroutines.flow.Flow
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.network.model.RelayConfiguration

interface RemoteDiscoveryRepository {
    val discoveredDevicesFlow: Flow<Set<Device>>

    val isRegisteredFlow: Flow<Boolean>

    fun setRelay(configuration: RelayConfiguration)
}
