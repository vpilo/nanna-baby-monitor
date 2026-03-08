package org.vpilo.babymonitor.model

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

typealias CameraFrameFlow = SharedFlow<CameraFrame>
typealias MutableCameraFrameFlow = MutableSharedFlow<CameraFrame>

fun makeMutableCameraFrameFlow(): MutableCameraFrameFlow =
    MutableSharedFlow(0, MAX_FRAME_BUFFER_SIZE, onBufferOverflow = BufferOverflow.DROP_OLDEST)

private const val MAX_FRAME_BUFFER_SIZE: Int = 100
