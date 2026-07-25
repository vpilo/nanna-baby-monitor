package org.vpilo.babymonitor.network.security.pairing

import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.network.model.pairing.PairedClient
import org.vpilo.babymonitor.network.model.pairing.PairedServer
import org.vpilo.babymonitor.network.model.repository.PairingRepository
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.repository.SettingsRepository
import org.vpilo.babymonitor.settings.model.settings.PairedClientsJson
import org.vpilo.babymonitor.settings.model.settings.PairedServersJson

internal class DefaultPairingRepository(
    private val settingsRepository: SettingsRepository,
) : PairingRepository {
    override val pairedServers =
        settingsRepository.flowOf(Setting.PairedServersJson).map { it.decodeServersOrEmpty() }

    override suspend fun pairServer(server: PairedServer) {
        val updated = loadServers().filterNot { it.deviceId == server.deviceId } + server
        settingsRepository.save(Setting.PairedServersJson, json.encodeToString(updated))
        Logger.i(TAG) { "Paired server ${server.deviceId}" }
    }

    override suspend fun findServer(deviceId: DeviceId): PairedServer? = loadServers().firstOrNull { it.deviceId == deviceId.toString() }

    override suspend fun unpairServer(deviceId: DeviceId) {
        val updated = loadServers().filterNot { it.deviceId == deviceId.toString() }
        settingsRepository.save(Setting.PairedServersJson, json.encodeToString(updated))
        Logger.i(TAG) { "Unpaired server $deviceId" }
    }

    override val pairedClients =
        settingsRepository.flowOf(Setting.PairedClientsJson).map { it.decodeClientsOrEmpty() }

    override suspend fun pairClient(client: PairedClient) {
        val updated = loadClients().filterNot { it.deviceId == client.deviceId } + client
        settingsRepository.save(Setting.PairedClientsJson, json.encodeToString(updated))
        Logger.i(TAG) { "Paired client ${client.deviceId}" }
    }

    override suspend fun findClient(clientId: DeviceId): PairedClient? = loadClients().firstOrNull { it.deviceId == clientId.toString() }

    override suspend fun revokeClient(clientId: DeviceId) {
        val updated = loadClients().filterNot { it.deviceId == clientId.toString() }
        settingsRepository.save(Setting.PairedClientsJson, json.encodeToString(updated))
        Logger.i(TAG) { "Revoked client $clientId" }
    }

    private suspend fun loadServers(): List<PairedServer> = settingsRepository.load(Setting.PairedServersJson).decodeServersOrEmpty()

    private suspend fun loadClients(): List<PairedClient> = settingsRepository.load(Setting.PairedClientsJson).decodeClientsOrEmpty()

    private fun String.decodeServersOrEmpty(): List<PairedServer> =
        runCatching { json.decodeFromString<List<PairedServer>>(this) }
            .onFailure { Logger.w(TAG, it) { "Failed to decode paired servers, resetting to empty" } }
            .getOrDefault(emptyList())

    private fun String.decodeClientsOrEmpty(): List<PairedClient> =
        runCatching { json.decodeFromString<List<PairedClient>>(this) }
            .onFailure { Logger.w(TAG, it) { "Failed to decode paired clients, resetting to empty" } }
            .getOrDefault(emptyList())

    private companion object {
        private val TAG = DefaultPairingRepository::class
        private val json = Json { ignoreUnknownKeys = true }
    }
}
