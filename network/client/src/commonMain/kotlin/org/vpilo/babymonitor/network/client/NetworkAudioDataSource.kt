package org.vpilo.babymonitor.network.client

import kotlinx.coroutines.flow.asSharedFlow
import org.vpilo.babymonitor.model.EncodedAudioStreamChunk
import org.vpilo.babymonitor.model.MutableStreamingAudioFlow
import org.vpilo.babymonitor.model.StreamingAudioFlow
import org.vpilo.babymonitor.model.makeMutableStreamingAudioFlow

internal class NetworkAudioDataSource {
    private val collector: MutableStreamingAudioFlow = makeMutableStreamingAudioFlow()

    val audioFrames: StreamingAudioFlow = collector.asSharedFlow()

    internal suspend fun onChunkReceived(chunk: EncodedAudioStreamChunk) {
        collector.emit(chunk)
    }
}
