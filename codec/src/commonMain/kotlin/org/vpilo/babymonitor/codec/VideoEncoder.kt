package org.vpilo.babymonitor.codec

import org.vpilo.babymonitor.model.MutableStreamingVideoFlow
import org.vpilo.babymonitor.model.OpaqueVideoStream
import kotlin.coroutines.CoroutineContext

expect class VideoEncoder(
    source: OpaqueVideoStream,
    output: MutableStreamingVideoFlow,
    coroutineContext: CoroutineContext,
) {
    fun start()

    fun stop()
}
