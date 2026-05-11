package org.vpilo.babymonitor.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

typealias AudioFrameFlow = Flow<AudioFrame>
typealias MutableAudioFrameFlow = MutableSharedFlow<AudioFrame>
