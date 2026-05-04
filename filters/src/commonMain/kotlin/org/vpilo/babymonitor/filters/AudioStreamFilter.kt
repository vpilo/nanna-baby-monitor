package org.vpilo.babymonitor.filters

import org.vpilo.babymonitor.model.AudioFrameFlow

abstract class AudioStreamFilter {
    abstract val output: AudioFrameFlow
}
