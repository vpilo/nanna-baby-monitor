package org.vpilo.babymonitor.network.client

import kotlinx.coroutines.flow.asSharedFlow
import org.vpilo.babymonitor.model.EncodedVideoStreamChunk
import org.vpilo.babymonitor.model.MutableStreamingVideoFlow
import org.vpilo.babymonitor.model.StreamingVideoFlow
import org.vpilo.babymonitor.model.makeMutableStreamingVideoFlow

internal class NetworkVideoDataSource {
    private val collector: MutableStreamingVideoFlow = makeMutableStreamingVideoFlow()

    val frames: StreamingVideoFlow = collector.asSharedFlow()

    internal suspend fun onChunkReceived(chunk: EncodedVideoStreamChunk) {
        collector.emit(chunk)
    }
}
