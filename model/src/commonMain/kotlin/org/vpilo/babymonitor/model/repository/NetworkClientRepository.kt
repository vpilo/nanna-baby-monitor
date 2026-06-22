package org.vpilo.babymonitor.model.repository

import kotlinx.coroutines.flow.Flow
import org.vpilo.babymonitor.model.Device

interface NetworkClientRepository {
    val connectionStateFlow: Flow<ConnectionState>

    val serverStateFlow: Flow<ServerState>

    val discoveredDevicesFlow: Flow<Set<Device>>

    fun identifySelf(device: Device.Client)

    suspend fun connect(server: Device.Server)

    suspend fun disconnect()

    fun setRelayHost(host: String)

    fun reset()
}
