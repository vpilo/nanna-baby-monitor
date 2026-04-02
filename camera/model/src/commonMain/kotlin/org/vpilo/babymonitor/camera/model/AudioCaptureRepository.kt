package org.vpilo.babymonitor.camera.model

import org.vpilo.babymonitor.model.AudioFrameFlow

/**
 * Repository for raw audio recording.
 * This repository provides uncompressed audio samples, as captured by a microphone.
 */
interface AudioCaptureRepository {
    val samples: AudioFrameFlow
}
