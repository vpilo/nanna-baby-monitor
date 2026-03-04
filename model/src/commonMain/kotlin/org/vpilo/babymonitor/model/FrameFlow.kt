package org.vpilo.babymonitor.model

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

typealias FrameFlow = SharedFlow<CameraFrameData>
typealias MutableFrameFlow = MutableSharedFlow<CameraFrameData>

fun makeMutableFrameFlow(): MutableFrameFlow =
    MutableSharedFlow(0, MAX_FRAME_BUFFER_SIZE, onBufferOverflow = BufferOverflow.DROP_OLDEST)

private const val MAX_FRAME_BUFFER_SIZE: Int = 100
