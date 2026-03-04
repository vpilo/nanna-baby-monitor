package org.vpilo.babymonitor.camera.model

import kotlinx.coroutines.flow.SharedFlow

interface CameraRepository {

    val frames: SharedFlow<CameraFrameData>
    val samples: SharedFlow<ByteArray>

    suspend fun start()

    suspend fun stop()

    fun isStarted(): Boolean
}
