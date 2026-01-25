package org.vpilo.babymonitor.camera.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.vpilo.babymonitor.camera.model.CameraFrameData

actual object CameraInterface {

    private val frameCollector: MutableSharedFlow<CameraFrameData> = MutableSharedFlow()

    private val sampleCollector: MutableSharedFlow<ByteArray> = MutableSharedFlow()

    actual val frames: SharedFlow<CameraFrameData> = frameCollector.asSharedFlow()
    actual val samples: SharedFlow<ByteArray> = sampleCollector.asSharedFlow()

    private val camera = DesktopCamera(frameCollector, sampleCollector)

    actual fun start() {
        camera.start()
    }

    actual fun stop() {
        camera.stop()
    }
}
