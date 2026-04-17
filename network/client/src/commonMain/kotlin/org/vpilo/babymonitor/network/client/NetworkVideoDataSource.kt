package org.vpilo.babymonitor.network.client

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.vpilo.babymonitor.model.EncodedAudioStreamChunk
import org.vpilo.babymonitor.model.EncodedVideoStreamChunk
import org.vpilo.babymonitor.model.MediaFormats
import org.vpilo.babymonitor.model.MutableStreamingAudioFlow
import org.vpilo.babymonitor.model.MutableStreamingVideoFlow
import org.vpilo.babymonitor.model.StreamingAudioFlow
import org.vpilo.babymonitor.model.StreamingVideoFlow
import org.vpilo.babymonitor.model.makeMutableStreamingAudioFlow
import org.vpilo.babymonitor.model.makeMutableStreamingVideoFlow

internal class NetworkVideoDataSource {
    private val collector: MutableStreamingVideoFlow = makeMutableStreamingVideoFlow()

    val frames: StreamingVideoFlow = collector.asSharedFlow()

    internal suspend fun onChunkReceived(chunk: EncodedVideoStreamChunk) {
        collector.emit(chunk)
    }
}
