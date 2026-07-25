package org.vpilo.babymonitor.network.server.websockets

import io.ktor.websocket.DefaultWebSocketSession
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.model.repository.StreamingAudioSenderRepository
import org.vpilo.babymonitor.network.common.protocol.runWebSocketCatching
import org.vpilo.babymonitor.network.common.repository.InternalActiveSessionsRepository
import org.vpilo.babymonitor.network.model.repository.PairingRepository
import org.vpilo.babymonitor.network.security.protocol.StreamType
import org.vpilo.babymonitor.network.security.protocol.protocolSendAudio
import org.vpilo.babymonitor.network.server.session.serverSessionHandshake

internal suspend fun DefaultWebSocketSession.audioStreamingServerWebSocket(serverDeviceId: DeviceId) {
    val pairingRepository = KoinPlatform.getKoin().get<PairingRepository>()
    val handshake = serverSessionHandshake(serverDeviceId, pairingRepository, StreamType.AUDIO) ?: return

    val sessionRegistry = KoinPlatform.getKoin().get<InternalActiveSessionsRepository>()
    sessionRegistry.register(handshake.clientId, this)
    try {
        coroutineScope {
            val repository = KoinPlatform.getKoin().get<StreamingAudioSenderRepository>()

            val senderJob =
                launch {
                    runWebSocketCatching(TAG) {
                        repository.chunks
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
        sessionRegistry.unregister(handshake.clientId, this)
    }
}

private const val TAG = "NetworkServer-Audio"
