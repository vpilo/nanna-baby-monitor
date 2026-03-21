package org.vpilo.babymonitor.model.repository

import kotlinx.coroutines.flow.Flow
import java.net.InetAddress

interface NetworkClientRepository {

    val stateFlow: Flow<NetworkState>

    val discoveredServers: Flow<Set<InetAddress>>

    suspend fun connect(address: InetAddress)
    suspend fun disconnect()
}
