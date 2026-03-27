package org.vpilo.babymonitor.model.repository

import kotlinx.coroutines.flow.Flow
import java.net.InetAddress

interface NetworkClientRepository {
    val connectionStateFlow: Flow<NetworkState>

    val serverStateFlow: Flow<ServerState>

    val discoveredServersFlow: Flow<Set<InetAddress>>

    suspend fun connect(address: InetAddress)

    suspend fun disconnect()
}
