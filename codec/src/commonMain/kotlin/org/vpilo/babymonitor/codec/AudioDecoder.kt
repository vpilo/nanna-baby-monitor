package org.vpilo.babymonitor.codec

import org.vpilo.babymonitor.model.MutableAudioFrameFlow
import org.vpilo.babymonitor.model.StreamingAudioFlow
import kotlin.coroutines.CoroutineContext

expect class AudioDecoder(
    input: StreamingAudioFlow,
    output: MutableAudioFrameFlow,
    coroutineContext: CoroutineContext,
) {
    fun start()

    fun stop()
}
