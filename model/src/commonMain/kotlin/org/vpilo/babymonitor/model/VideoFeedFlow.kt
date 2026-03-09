package org.vpilo.babymonitor.model

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

typealias VideoFeedFlow = SharedFlow<EncodedStreamChunk>
typealias MutableVideoFeedFlow = MutableSharedFlow<EncodedStreamChunk>

fun makeMutableVideoFeedFlow(): MutableVideoFeedFlow =
    MutableSharedFlow(0, MAX_STREAM_BUFFER_SIZE, onBufferOverflow = BufferOverflow.DROP_OLDEST)

private const val MAX_STREAM_BUFFER_SIZE: Int = 100
