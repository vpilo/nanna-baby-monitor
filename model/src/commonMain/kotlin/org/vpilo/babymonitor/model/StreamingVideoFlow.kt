package org.vpilo.babymonitor.model

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

typealias StreamingVideoFlow = Flow<EncodedVideoStreamChunk>
typealias MutableStreamingVideoFlow = MutableSharedFlow<EncodedVideoStreamChunk>

fun makeMutableStreamingVideoFlow(): MutableStreamingVideoFlow =
    MutableSharedFlow(0, MediaFormats.BufferSizes.MAX_VIDEO_STREAM_BUFFER_SIZE, onBufferOverflow = BufferOverflow.DROP_OLDEST)
