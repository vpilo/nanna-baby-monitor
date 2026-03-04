package org.vpilo.babymonitor.camera.data

import kotlinx.coroutines.flow.SharedFlow
import org.vpilo.babymonitor.camera.model.CameraRepository
import org.vpilo.babymonitor.camera.model.CameraFrameData

internal class DefaultCameraRepository(
) : CameraRepository {

    override val frames: SharedFlow<CameraFrameData> = CameraInterface.frames
    override val samples: SharedFlow<ByteArray> = CameraInterface.samples

    override suspend fun start() {
        CameraInterface.start()
    }

    override suspend fun stop() {
        CameraInterface.stop()
    }

    override fun isStarted(): Boolean =
        CameraInterface.isStarted()
}
