package org.vpilo.babymonitor.model.repository

import kotlinx.coroutines.flow.Flow

interface NetworkClientRepository {
    val connectionStateFlow: Flow<NetworkState>

    val serverStateFlow: Flow<ServerState>

    val discoveredServerIdsFlow: Flow<Set<ServerId>>

    suspend fun connect(server: ServerId)

    suspend fun reconnect()

    suspend fun disconnect()

    fun setRelayHost(host: String)

    fun setDeviceName(name: String)
}
