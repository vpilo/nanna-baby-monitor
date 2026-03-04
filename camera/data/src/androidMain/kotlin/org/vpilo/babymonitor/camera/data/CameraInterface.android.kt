package org.vpilo.babymonitor.camera.data

import kotlinx.coroutines.flow.asSharedFlow
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.vpilo.babymonitor.model.FrameFlow
import org.vpilo.babymonitor.model.MutableFrameFlow
import org.vpilo.babymonitor.model.MutableSampleFlow
import org.vpilo.babymonitor.model.SampleFlow
import org.vpilo.babymonitor.model.makeMutableFrameFlow
import org.vpilo.babymonitor.model.makeMutableSampleFlow

actual object CameraInterface : KoinComponent {
    internal val frameCollector: MutableFrameFlow = makeMutableFrameFlow()

    internal val sampleCollector: MutableSampleFlow = makeMutableSampleFlow()

    actual val frames: FrameFlow = frameCollector.asSharedFlow()

    actual val samples: SampleFlow = sampleCollector.asSharedFlow()

    actual fun start() {
        AndroidBackgroundService.start(get())
    }

    actual fun stop() {
        AndroidBackgroundService.stop(get())
    }

    actual fun isStarted() =
        AndroidBackgroundService.isStarted()
}
