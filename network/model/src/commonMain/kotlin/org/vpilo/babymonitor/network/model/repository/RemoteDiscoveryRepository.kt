package org.vpilo.babymonitor.network.model.repository

import kotlinx.coroutines.flow.Flow
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.network.model.transport.VersionMismatch

interface RemoteDiscoveryRepository {
    val discoveredDevicesFlow: Flow<Set<Device>>

    val isRegisteredFlow: Flow<Boolean>

    val relayVersionMismatchFlow: Flow<VersionMismatch?>

    suspend fun start()

    suspend fun stop()
}
