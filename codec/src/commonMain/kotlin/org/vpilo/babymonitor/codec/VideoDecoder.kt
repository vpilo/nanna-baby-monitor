package org.vpilo.babymonitor.codec

import org.vpilo.babymonitor.model.OpaqueVideoStream
import org.vpilo.babymonitor.model.StreamingVideoFlow
import kotlin.coroutines.CoroutineContext

expect class VideoDecoder(
    input: StreamingVideoFlow,
    coroutineContext: CoroutineContext,
) {
    val videoStream: OpaqueVideoStream

    fun start()

    fun stop()
}
