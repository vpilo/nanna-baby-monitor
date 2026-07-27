package org.vpilo.babymonitor.filters

import org.vpilo.babymonitor.model.AudioFrameFlow

interface AudioStreamFilter {
    val output: AudioFrameFlow
}
