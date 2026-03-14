package org.vpilo.babymonitor.app

import org.vpilo.babymonitor.model.AudioFrameFlow
import kotlin.coroutines.CoroutineContext

expect class AudioPlayer(
    input: AudioFrameFlow,
    coroutineContext: CoroutineContext,
)
