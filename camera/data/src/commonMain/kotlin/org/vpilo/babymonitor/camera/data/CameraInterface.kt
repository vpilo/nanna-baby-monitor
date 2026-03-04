package org.vpilo.babymonitor.camera.data

import kotlinx.coroutines.flow.SharedFlow
import org.vpilo.babymonitor.model.CameraFrameData
import org.vpilo.babymonitor.model.FrameFlow
import org.vpilo.babymonitor.model.SampleFlow

expect object CameraInterface {

    val frames: FrameFlow
    val samples: SampleFlow

    fun start()

    fun stop()

    fun isStarted(): Boolean
}
