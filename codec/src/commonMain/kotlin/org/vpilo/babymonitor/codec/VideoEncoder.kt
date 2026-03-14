package org.vpilo.babymonitor.codec

import org.vpilo.babymonitor.model.CameraFrameFlow
import org.vpilo.babymonitor.model.MutableStreamingVideoFlow
import kotlin.coroutines.CoroutineContext

expect class VideoEncoder(
    input: CameraFrameFlow,
    output: MutableStreamingVideoFlow,
    coroutineContext: CoroutineContext,
) {
    fun start()
    fun stop()
}
