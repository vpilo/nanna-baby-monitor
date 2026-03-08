package org.vpilo.babymonitor.model

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

typealias VideoFeedFlow = SharedFlow<ByteArray>
typealias MutableVideoFeedFlow = MutableSharedFlow<ByteArray>

fun makeMutableVideoFeedFlow(): MutableVideoFeedFlow =
    MutableSharedFlow(0, MAX_SAMPLE_BUFFER_SIZE, onBufferOverflow = BufferOverflow.DROP_OLDEST)

private const val MAX_SAMPLE_BUFFER_SIZE: Int = 100
