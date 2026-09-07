package org.vpilo.babymonitor.network.client

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.codec.AudioDecoder
import org.vpilo.babymonitor.model.AudioFrame
import org.vpilo.babymonitor.model.AudioFrameFlow
import org.vpilo.babymonitor.model.MediaFormats
import org.vpilo.babymonitor.model.repository.LocalClientDeviceRepository
import org.vpilo.babymonitor.model.repository.SharedResourceHolder
import org.vpilo.babymonitor.model.repository.StreamingAudioReceiverRepository
import org.vpilo.babymonitor.network.client.websockets.audioStreamingClientWebSocket
import org.vpilo.babymonitor.network.internal.repository.InternalActiveSessionsRepository
import org.vpilo.babymonitor.network.model.Constants
import org.vpilo.babymonitor.network.model.Endpoints
import org.vpilo.babymonitor.network.model.repository.PairingStorageRepository
import org.vpilo.babymonitor.network.model.repository.RelayConfigurationRepository
import kotlin.coroutines.CoroutineContext

internal class NetworkAudioReceiverRepository(
    private val dataSource: NetworkAudioDataSource,
    private val serverSelectionDataSource: ServerSelectionDataSource,
    private val pairingStorageRepository: PairingStorageRepository,
    private val relayConfigurationRepository: RelayConfigurationRepository,
    private val activeSessionsRepository: InternalActiveSessionsRepository,
    private val localClientDeviceRepository: LocalClientDeviceRepository,
    coroutineContext: CoroutineContext,
) : SharedResourceHolder<AudioFrame>(
        bufferCapacity = MediaFormats.BufferSizes.MAX_SAMPLE_BUFFER_SIZE,
    ),
    StreamingAudioReceiverRepository {
    override val chunks: AudioFrameFlow = collector.asSharedFlow()

    private val decoder: AudioDecoder =
        AudioDecoder(
            input = dataSource.audioFrames,
            output = collector,
            coroutineContext = coroutineContext,
        )

    private var handler: WebSocketConnectionHandler? = null
    private var connectionJob: Job? = null

    override fun start() {
        connectionJob =
            coroutineScope.launch {
                try {
                    serverSelectionDataSource.server.collect { target ->
                        handler?.disconnect()
                        handler = null
                        if (target == null) return@collect
                        val localDevice = localClientDeviceRepository.localDevice.first()
                        val expectedFingerprint =
                            pairingStorageRepository.findServer(target.id)?.certFingerprint
                                ?: error("Not paired with $target - unable to connect")
                        handler =
                            WebSocketConnectionHandler(
                                device = target,
                                endpointPath = Endpoints.STREAM_AUDIO,
                                sessionBlock = {
                                    audioStreamingClientWebSocket(
                                        localDevice = localDevice,
                                        serverDeviceId = target.id,
                                        dataSource = dataSource,
                                        pairingStorageRepository = pairingStorageRepository,
                                        activeSessionsRepository = activeSessionsRepository,
                                    )
                                },
                                expectedFingerprint = expectedFingerprint,
                                relayConfiguration = relayConfigurationRepository.relayConfiguration.first(),
                                coroutineScope = coroutineScope,
                                onDisconnected = {
                                    delay(Constants.RECONNECTION_TIMEOUT)
                                    handler?.connect()
                                },
                            ).apply { connect() }
                    }
                } finally {
                    handler?.disconnect()
                    handler = null
                }
            }
        decoder.start()
    }

    override fun stop() {
        connectionJob?.cancel()
        connectionJob = null
        decoder.stop()
    }
}
