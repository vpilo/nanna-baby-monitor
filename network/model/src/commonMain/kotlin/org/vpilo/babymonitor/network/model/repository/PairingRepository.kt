package org.vpilo.babymonitor.network.model.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.model.repository.DeviceId

@Serializable
data class PairedServer(
    val deviceId: String,
    val name: String,
    val certFingerprint: String,
    val sharedSecretBase64: String,
) {
    fun asDevice(): Device = Device.LocalServer(DeviceId.parse(deviceId), name)
}

@Serializable
data class PairedClient(
    val deviceId: String,
    val name: String,
    val sharedSecretBase64: String,
    val pairedAtEpochMillis: Long,
) {
    fun asDevice(): Device = Device.Client(DeviceId.parse(deviceId), name)
}

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
