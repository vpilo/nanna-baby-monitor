package org.vpilo.babymonitor.network.client.websockets

import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.network.client.NetworkControlDataSource
import org.vpilo.babymonitor.network.client.session.clientSessionHandshake
import org.vpilo.babymonitor.network.internal.protocol.ServerMessage
import org.vpilo.babymonitor.network.internal.protocol.runWebSocketCatching
import org.vpilo.babymonitor.network.internal.repository.InternalActiveSessionsRepository
import org.vpilo.babymonitor.network.model.repository.PairingStorageRepository
import org.vpilo.babymonitor.network.security.protocol.StreamType
import org.vpilo.babymonitor.network.security.protocol.receiveServerMessage

internal suspend fun DefaultClientWebSocketSession.controlClientWebSocket(
    localDevice: Device.Client,
    serverDeviceId: DeviceId,
    dataSource: NetworkControlDataSource,
    pairingStorageRepository: PairingStorageRepository,
    activeSessionsRepository: InternalActiveSessionsRepository,
) {
    val pairedDevice = pairingStorageRepository.find(serverDeviceId)
    val cipher = clientSessionHandshake(localDevice.id, serverDeviceId, pairedDevice, StreamType.CONTROL) ?: return

    activeSessionsRepository.register(serverDeviceId, this)
    try {
        runWebSocketCatching(TAG) {
            while (true) {
                when (val message = receiveServerMessage(cipher)) {
                    is ServerMessage.State -> dataSource.onServerStateReceived(message.payload)
                }
            }
        }
    } finally {
        activeSessionsRepository.unregister(serverDeviceId, this)
    }
}

private const val TAG = "NetworkClient-Control"
