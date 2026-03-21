package org.vpilo.babymonitor.model

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

typealias AudioFrameFlow = Flow<AudioFrame>
typealias MutableAudioFrameFlow = MutableSharedFlow<AudioFrame>

fun makeMutableAudioFrameFlow(): MutableAudioFrameFlow =
    MutableSharedFlow(0, MediaFormats.BufferSizes.MAX_SAMPLE_BUFFER_SIZE, onBufferOverflow = BufferOverflow.DROP_OLDEST)
