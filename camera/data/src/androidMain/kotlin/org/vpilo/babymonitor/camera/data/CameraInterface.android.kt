package org.vpilo.babymonitor.camera.data

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.asSharedFlow
import org.koin.core.component.KoinComponent
import org.vpilo.babymonitor.android.service.AndroidService
import org.vpilo.babymonitor.android.service.AndroidServiceRegistry
import org.vpilo.babymonitor.model.FrameFlow
import org.vpilo.babymonitor.model.MutableFrameFlow
import org.vpilo.babymonitor.model.MutableSampleFlow
import org.vpilo.babymonitor.model.SampleFlow
import org.vpilo.babymonitor.model.makeMutableFrameFlow
import org.vpilo.babymonitor.model.makeMutableSampleFlow

actual object CameraInterface : KoinComponent, AndroidService {
    internal val frameCollector: MutableFrameFlow = makeMutableFrameFlow()

    internal val sampleCollector: MutableSampleFlow = makeMutableSampleFlow()

    actual val frames: FrameFlow = frameCollector.asSharedFlow()

    actual val samples: SampleFlow = sampleCollector.asSharedFlow()

    private val camera: AndroidCamera = AndroidCamera(videoFrames = frameCollector, audioSamples = sampleCollector)

    actual fun start() {
        AndroidServiceRegistry.register(this)
    }

    actual fun stop() {
        AndroidServiceRegistry.unregister(this)
    }

    actual fun isStarted() =
        AndroidServiceRegistry.isServiceRunning()

    override fun onServiceStarted(context: Context, lifecycleOwner: LifecycleOwner) {
        camera.start(context, lifecycleOwner)
    }

    override fun onServiceStopped() {
        camera.stop()
    }

}
