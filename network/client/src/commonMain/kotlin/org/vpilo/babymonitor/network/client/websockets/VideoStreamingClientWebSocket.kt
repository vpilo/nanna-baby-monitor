package org.vpilo.babymonitor.network.client.websockets

import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.network.client.NetworkVideoDataSource
import org.vpilo.babymonitor.network.client.session.clientSessionHandshake
import org.vpilo.babymonitor.network.internal.protocol.runWebSocketCatching
import org.vpilo.babymonitor.network.internal.repository.InternalActiveSessionsRepository
import org.vpilo.babymonitor.network.model.repository.PairingStorageRepository
import org.vpilo.babymonitor.network.security.protocol.StreamType
import org.vpilo.babymonitor.network.security.protocol.protocolReceiveVideo

internal suspend fun DefaultClientWebSocketSession.videoStreamingClientWebSocket(
    localDevice: Device.Client,
    serverDeviceId: DeviceId,
    dataSource: NetworkVideoDataSource,
    pairingStorageRepository: PairingStorageRepository,
    activeSessionsRepository: InternalActiveSessionsRepository,
) {
    val pairedServer = pairingStorageRepository.findServer(serverDeviceId)
    val cipher = clientSessionHandshake(localDevice.id, serverDeviceId, pairedServer, StreamType.VIDEO) ?: return

    activeSessionsRepository.register(serverDeviceId, this)
    try {
        runWebSocketCatching(TAG) {
            while (true) {
                val frame = protocolReceiveVideo(cipher)
                dataSource.onChunkReceived(frame)
            }
        }
    } finally {
        activeSessionsRepository.unregister(serverDeviceId, this)
    }
}

private const val TAG = "NetworkClient-Video"
