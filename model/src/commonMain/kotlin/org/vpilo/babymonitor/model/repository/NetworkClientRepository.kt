package org.vpilo.babymonitor.model.repository

import kotlinx.coroutines.flow.Flow
import org.vpilo.babymonitor.model.Device

interface NetworkClientRepository {
    val connectionStateFlow: Flow<ConnectionState>

    val serverStateFlow: Flow<ServerState>

    suspend fun connect(server: Device.Server)

    suspend fun pairWith(
        server: Device.Server,
        pin: String,
    ): ClientPairingState

    suspend fun disconnect()

    fun reset()
}
