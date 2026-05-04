package org.vpilo.babymonitor.filters

import org.vpilo.babymonitor.model.CameraFrameFlow

abstract class VideoStreamFilter {
    abstract val output: CameraFrameFlow
}
