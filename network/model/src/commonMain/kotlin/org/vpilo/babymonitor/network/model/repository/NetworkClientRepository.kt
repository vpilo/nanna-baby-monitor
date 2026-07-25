package org.vpilo.babymonitor.network.model.repository

import kotlinx.coroutines.flow.Flow
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.model.repository.ConnectionState
import org.vpilo.babymonitor.network.model.ServerState
import org.vpilo.babymonitor.network.model.pairing.ClientPairingState
import org.vpilo.babymonitor.network.model.pairing.Pin

interface NetworkClientRepository {
    val connectionStateFlow: Flow<ConnectionState>

    val serverStateFlow: Flow<ServerState>

    suspend fun connect(server: Device.Server)

    suspend fun disconnect()

    fun reset()
}
