package org.vpilo.babymonitor.network.client.websockets

import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import org.koin.mp.KoinPlatform
import org.vpilo.babymonitor.model.EncodedAudioStreamChunk
import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.model.repository.toDeviceId
import org.vpilo.babymonitor.network.client.NetworkAudioDataSource
import org.vpilo.babymonitor.network.client.session.clientSessionHandshake
import org.vpilo.babymonitor.network.common.protocol.StreamType
import org.vpilo.babymonitor.network.common.protocol.protocolReceiveAudio
import org.vpilo.babymonitor.network.common.protocol.runWebSocketCatching
import org.vpilo.babymonitor.network.model.repository.PairingRepository
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.repository.SettingsRepository
import org.vpilo.babymonitor.settings.model.settings.DeviceId

internal suspend fun DefaultClientWebSocketSession.audioStreamingClientWebSocket(serverDeviceId: DeviceId) {
    val dataSource = KoinPlatform.getKoin().get<NetworkAudioDataSource>()
    val pairingRepository = KoinPlatform.getKoin().get<PairingRepository>()
    val settingsRepository = KoinPlatform.getKoin().get<SettingsRepository>()
    val clientId = settingsRepository.load(Setting.DeviceId).toDeviceId()

    val cipher = clientSessionHandshake(clientId, serverDeviceId, pairingRepository, StreamType.AUDIO) ?: return

    runWebSocketCatching(TAG) {
        while (true) {
            val frame: EncodedAudioStreamChunk = protocolReceiveAudio(cipher)
            dataSource.onChunkReceived(frame)
        }
    }
}

private const val TAG = "NetworkClient-Audio"
