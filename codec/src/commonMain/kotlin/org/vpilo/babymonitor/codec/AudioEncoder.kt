package org.vpilo.babymonitor.codec

import org.vpilo.babymonitor.model.AudioFrameFlow
import org.vpilo.babymonitor.model.MutableStreamingAudioFlow
import kotlin.coroutines.CoroutineContext

expect class AudioEncoder(
    input: AudioFrameFlow,
    output: MutableStreamingAudioFlow,
    coroutineContext: CoroutineContext,
) {
    fun start()
    fun stop()
}
