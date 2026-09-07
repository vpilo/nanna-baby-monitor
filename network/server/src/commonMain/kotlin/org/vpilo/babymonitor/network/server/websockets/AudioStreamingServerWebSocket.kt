package org.vpilo.babymonitor.network.server.websockets

import io.ktor.websocket.DefaultWebSocketSession
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.model.repository.StreamingAudioSenderRepository
import org.vpilo.babymonitor.network.internal.protocol.runWebSocketCatching
import org.vpilo.babymonitor.network.internal.repository.InternalActiveSessionsRepository
import org.vpilo.babymonitor.network.model.repository.PairingStorageRepository
import org.vpilo.babymonitor.network.security.protocol.StreamType
import org.vpilo.babymonitor.network.security.protocol.protocolSendAudio
import org.vpilo.babymonitor.network.server.session.serverSessionHandshake

internal suspend fun DefaultWebSocketSession.audioStreamingServerWebSocket(
    serverDeviceId: DeviceId,
    pairingStorageRepository: PairingStorageRepository,
    activeSessionsRepository: InternalActiveSessionsRepository,
    streamingAudioSenderRepository: StreamingAudioSenderRepository,
) {
    val handshake = serverSessionHandshake(serverDeviceId, pairingStorageRepository, StreamType.AUDIO) ?: return

    activeSessionsRepository.register(handshake.clientId, this)
    try {
        coroutineScope {
            val senderJob =
                launch {
                    runWebSocketCatching(TAG) {
                        streamingAudioSenderRepository.chunks
                            .collect {
                                protocolSendAudio(it, handshake.cipher)
                            }
                    }
                }

            // Drain incoming only to detect session closure.
            val readerJob =
                launch {
                    for (frame in incoming) {
                        Logger.w(TAG) { "Unexpected frame from client: $frame" }
                    }
                }

            senderJob.invokeOnCompletion { readerJob.cancel() }
            readerJob.invokeOnCompletion { senderJob.cancel() }
        }
    } finally {
        activeSessionsRepository.unregister(handshake.clientId, this)
    }
}

private const val TAG = "NetworkServer-Audio"
