package org.vpilo.babymonitor.camera.data

import kotlinx.coroutines.flow.asSharedFlow
import org.vpilo.babymonitor.model.FrameFlow
import org.vpilo.babymonitor.model.MutableFrameFlow
import org.vpilo.babymonitor.model.MutableSampleFlow
import org.vpilo.babymonitor.model.SampleFlow
import org.vpilo.babymonitor.model.makeMutableFrameFlow
import org.vpilo.babymonitor.model.makeMutableSampleFlow

actual object CameraInterface {

    private val frameCollector: MutableFrameFlow = makeMutableFrameFlow()

    private val sampleCollector: MutableSampleFlow = makeMutableSampleFlow()

    actual val frames: FrameFlow = frameCollector.asSharedFlow()
    actual val samples: SampleFlow = sampleCollector.asSharedFlow()

    private val camera = DesktopCamera(frameCollector, sampleCollector)

    actual fun start() {
        camera.start()
    }

    actual fun stop() {
        camera.stop()
    }

    actual fun isStarted(): Boolean = camera.isStarted()
}
