package org.vpilo.babymonitor.camera.data

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.onEach
import org.vpilo.babymonitor.camera.model.CameraFrameData
import org.vpilo.babymonitor.camera.model.CameraRepository
import org.vpilo.babymonitor.common.Logger

actual object CameraInterface {

    private val frameCollector: MutableSharedFlow<CameraFrameData> =
        MutableSharedFlow(0, MAX_FRAME_BUFFER_SIZE, onBufferOverflow = BufferOverflow.DROP_OLDEST)

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
