package org.vpilo.babymonitor.camera.data

import kotlinx.coroutines.flow.SharedFlow
import org.vpilo.babymonitor.camera.model.CameraFrameData

expect object CameraInterface {

    val frames: SharedFlow<CameraFrameData>
    val samples: SharedFlow<ByteArray>

    fun start()

    fun stop()

    fun isStarted(): Boolean
}

internal const val MAX_FRAME_BUFFER_SIZE: Int = 100
