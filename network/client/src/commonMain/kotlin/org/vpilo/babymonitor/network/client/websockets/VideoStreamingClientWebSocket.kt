package org.vpilo.babymonitor.network.client.websockets

import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import org.koin.mp.KoinPlatform
import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.model.repository.toDeviceId
import org.vpilo.babymonitor.network.client.NetworkVideoDataSource
import org.vpilo.babymonitor.network.client.session.clientSessionHandshake
import org.vpilo.babymonitor.network.internal.protocol.runWebSocketCatching
import org.vpilo.babymonitor.network.internal.repository.InternalActiveSessionsRepository
import org.vpilo.babymonitor.network.model.repository.PairingStorageRepository
import org.vpilo.babymonitor.network.security.protocol.StreamType
import org.vpilo.babymonitor.network.security.protocol.protocolReceiveVideo
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.repository.SettingsRepository
import org.vpilo.babymonitor.settings.model.settings.DeviceId

internal suspend fun DefaultClientWebSocketSession.videoStreamingClientWebSocket(serverDeviceId: DeviceId) {
    val koin = KoinPlatform.getKoin()
    val dataSource = koin.get<NetworkVideoDataSource>()
    val pairingStorageRepository = koin.get<PairingStorageRepository>()
    val settingsRepository = koin.get<SettingsRepository>()
    val sessionRegistry = koin.get<InternalActiveSessionsRepository>()

    val clientId = settingsRepository.load(Setting.DeviceId).toDeviceId()
    val pairedServer = pairingStorageRepository.findServer(serverDeviceId)
    val cipher = clientSessionHandshake(clientId, serverDeviceId, pairedServer, StreamType.VIDEO) ?: return

    sessionRegistry.register(serverDeviceId, this)
    try {
        runWebSocketCatching(TAG) {
            while (true) {
                val frame = protocolReceiveVideo(cipher)
                dataSource.onChunkReceived(frame)
            }
        }
    } finally {
        sessionRegistry.unregister(serverDeviceId, this)
    }
}

private const val TAG = "NetworkClient-Video"
