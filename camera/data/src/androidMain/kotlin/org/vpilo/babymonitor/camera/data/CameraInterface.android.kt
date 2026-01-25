package org.vpilo.babymonitor.camera.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.vpilo.babymonitor.camera.model.CameraFrameData

actual object CameraInterface: KoinComponent {
    internal val frameCollector: MutableSharedFlow<CameraFrameData> = MutableSharedFlow()

    internal val sampleCollector: MutableSharedFlow<ByteArray> = MutableSharedFlow()

    actual val frames: SharedFlow<CameraFrameData> = frameCollector.asSharedFlow()

    actual val samples: SharedFlow<ByteArray> = sampleCollector.asSharedFlow()

    actual fun start() {
        AndroidBackgroundService.start(get())
    }

    actual fun stop() {
        AndroidBackgroundService.stop(get())
    }
}
