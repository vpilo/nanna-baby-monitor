package org.vpilo.babymonitor.network.security.pairing

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.network.model.pairing.PairedClient
import org.vpilo.babymonitor.network.model.pairing.PairedServer
import org.vpilo.babymonitor.network.model.repository.PairingStorageRepository
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.repository.SettingsRepository
import org.vpilo.babymonitor.settings.model.settings.PairedClientsJson
import org.vpilo.babymonitor.settings.model.settings.PairedServersJson

internal class DefaultPairingStorageRepository(
    private val settingsRepository: SettingsRepository,
) : PairingStorageRepository {
    override val pairedServers =
        settingsRepository
            .flowOf(Setting.PairedServersJson)
            .map { it.decodeServersOrEmpty() }

    override val pairedClients =
        settingsRepository
            .flowOf(Setting.PairedClientsJson)
            .map { it.decodeClientsOrEmpty() }

    override suspend fun pairServer(server: PairedServer) {
        val updated = getServers().filterNot { it.deviceId == server.deviceId } + server
        settingsRepository.save(Setting.PairedServersJson, json.encodeToString(updated))
        Logger.i(TAG) { "Paired server ${server.deviceId}" }
    }

    override suspend fun findServer(deviceId: DeviceId): PairedServer? = getServers().firstOrNull { it.deviceId == deviceId.toString() }

    override suspend fun unpairServer(deviceId: DeviceId) {
        val updated = getServers().filterNot { it.deviceId == deviceId.toString() }
        settingsRepository.save(Setting.PairedServersJson, json.encodeToString(updated))
        Logger.i(TAG) { "Unpaired server $deviceId" }
    }

    override suspend fun pairClient(client: PairedClient) {
        val updated = getClients().filterNot { it.deviceId == client.deviceId } + client
        settingsRepository.save(Setting.PairedClientsJson, json.encodeToString(updated))
        Logger.i(TAG) { "Paired client ${client.deviceId}" }
    }

    override suspend fun findClient(clientId: DeviceId): PairedClient? = getClients().firstOrNull { it.deviceId == clientId.toString() }

    override suspend fun revokeClient(clientId: DeviceId) {
        val updated = getClients().filterNot { it.deviceId == clientId.toString() }
        settingsRepository.save(Setting.PairedClientsJson, json.encodeToString(updated))
        Logger.i(TAG) { "Revoked client $clientId" }
    }

    override suspend fun updateName(
        clientId: DeviceId,
        newName: String,
    ) {
        val id = clientId.toString()
        val isClient = getClients().any { it.deviceId == clientId.toString() }
        val setting = if (isClient) Setting.PairedClientsJson else Setting.PairedServersJson

        val updated =
            if (isClient) {
                getClients().map {
                    if (it.deviceId == id) it.copy(name = newName) else it
                }
            } else {
                getServers().map {
                    if (it.deviceId == id) it.copy(name = newName) else it
                }
            }
        settingsRepository.save(setting, json.encodeToString(updated))
        Logger.i(TAG) { "Updated stored name for paired device $id to $newName" }
    }

    private suspend fun getServers(): List<PairedServer> = pairedServers.first()

    private suspend fun getClients(): List<PairedClient> = pairedClients.first()

    private fun String.decodeServersOrEmpty(): List<PairedServer> =
        runCatching { json.decodeFromString<List<PairedServer>>(this) }
            .onFailure { Logger.w(TAG, it) { "Failed to decode paired servers, resetting to empty" } }
            .getOrDefault(emptyList())

    private fun String.decodeClientsOrEmpty(): List<PairedClient> =
        runCatching { json.decodeFromString<List<PairedClient>>(this) }
            .onFailure { Logger.w(TAG, it) { "Failed to decode paired clients, resetting to empty" } }
            .getOrDefault(emptyList())

    private companion object {
        private val TAG = DefaultPairingStorageRepository::class
        private val json = Json { ignoreUnknownKeys = true }
    }
}
