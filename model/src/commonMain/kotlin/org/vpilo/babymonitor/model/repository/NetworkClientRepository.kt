package org.vpilo.babymonitor.model.repository

import kotlinx.coroutines.flow.Flow

interface NetworkClientRepository {
    val connectionStateFlow: Flow<NetworkState>

    val serverStateFlow: Flow<ServerState>

    val localServerIdsFlow: Flow<Set<ServerId>>

    val relayServerIdsFlow: Flow<Set<ServerId>>

    suspend fun connect(server: ServerId)

    suspend fun disconnect()

    fun enableAudio(enable: Boolean)

    fun enableVideo(enable: Boolean)

    fun setRelayHost(host: String)

    fun setDeviceName(name: String)
}
