package org.vpilo.babymonitor.model

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

typealias StreamingAudioFlow = Flow<EncodedAudioStreamChunk>

typealias MutableStreamingAudioFlow = MutableSharedFlow<EncodedAudioStreamChunk>

fun makeMutableStreamingAudioFlow(): MutableStreamingAudioFlow =
    MutableSharedFlow(0, MediaFormats.BufferSizes.MAX_SAMPLE_BUFFER_SIZE, onBufferOverflow = BufferOverflow.DROP_OLDEST)
