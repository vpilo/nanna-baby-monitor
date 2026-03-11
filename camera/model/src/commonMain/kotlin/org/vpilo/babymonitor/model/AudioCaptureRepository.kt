package org.vpilo.babymonitor.model

/**
 * Repository for raw audio recording.
 * This repository provides uncompressed audio samples, as captured by a microphone.
 */
interface AudioCaptureRepository {
    val samples: AudioFrameFlow
}
