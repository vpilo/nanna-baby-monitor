package org.vpilo.babymonitor.camera.data

import org.vpilo.babymonitor.model.AudioFrameFlow

internal expect class AudioCaptureDataSource() {
    val samples: AudioFrameFlow
}
