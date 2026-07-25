package org.vpilo.babymonitor.network.model.repository

import kotlinx.coroutines.flow.Flow
import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.network.model.pairing.PairedClient
import org.vpilo.babymonitor.network.model.pairing.PairedServer

interface PairingStorageRepository {
    val pairedServers: Flow<List<PairedServer>>

    suspend fun pairServer(server: PairedServer)

    suspend fun findServer(deviceId: DeviceId): PairedServer?

    suspend fun unpairServer(deviceId: DeviceId)

    val pairedClients: Flow<List<PairedClient>>

    suspend fun pairClient(client: PairedClient)

    suspend fun findClient(clientId: DeviceId): PairedClient?

    suspend fun revokeClient(clientId: DeviceId)
}
