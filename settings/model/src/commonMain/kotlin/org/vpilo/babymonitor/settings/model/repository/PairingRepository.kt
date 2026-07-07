package org.vpilo.babymonitor.settings.model.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import org.vpilo.babymonitor.model.repository.DeviceId

@Serializable
data class PairedServer(
    val deviceId: String,
    val name: String,
    val certFingerprint: String,
    val sharedSecretBase64: String,
)

@Serializable
data class PairedClient(
    val clientId: String,
    val name: String,
    val sharedSecretBase64: String,
    val pairedAtEpochMillis: Long,
)

interface PairingRepository {
    val pairedServers: Flow<List<PairedServer>>

    suspend fun pairServer(server: PairedServer)

    suspend fun findServer(deviceId: DeviceId): PairedServer?

    suspend fun unpairServer(deviceId: DeviceId)

    val pairedClients: Flow<List<PairedClient>>

    suspend fun pairClient(client: PairedClient)

    suspend fun findClient(clientId: DeviceId): PairedClient?

    suspend fun revokeClient(clientId: DeviceId)
}
