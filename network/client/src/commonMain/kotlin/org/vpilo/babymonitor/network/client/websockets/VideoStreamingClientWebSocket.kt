package org.vpilo.babymonitor.network.client.websockets

import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import org.koin.mp.KoinPlatform
import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.model.repository.toDeviceId
import org.vpilo.babymonitor.network.client.NetworkVideoDataSource
import org.vpilo.babymonitor.network.client.session.clientSessionHandshake
import org.vpilo.babymonitor.network.common.protocol.runWebSocketCatching
import org.vpilo.babymonitor.network.common.repository.InternalActiveSessionsRepository
import org.vpilo.babymonitor.network.model.repository.PairingRepository
import org.vpilo.babymonitor.network.security.protocol.StreamType
import org.vpilo.babymonitor.network.security.protocol.protocolReceiveVideo
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.repository.SettingsRepository
import org.vpilo.babymonitor.settings.model.settings.DeviceId

internal suspend fun DefaultClientWebSocketSession.videoStreamingClientWebSocket(serverDeviceId: DeviceId) {
    val dataSource = KoinPlatform.getKoin().get<NetworkVideoDataSource>()
    val pairingRepository = KoinPlatform.getKoin().get<PairingRepository>()
    val settingsRepository = KoinPlatform.getKoin().get<SettingsRepository>()
    val clientId = settingsRepository.load(Setting.DeviceId).toDeviceId()

    val cipher = clientSessionHandshake(clientId, serverDeviceId, pairingRepository, StreamType.VIDEO) ?: return

    val sessionRegistry = KoinPlatform.getKoin().get<InternalActiveSessionsRepository>()
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
