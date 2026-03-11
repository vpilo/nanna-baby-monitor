package org.vpilo.babymonitor.model

/**
 * Repository for raw captured camera frames.
 * This repository provides uncompressed camera frames, as captured by a webcam or camera.
 */
interface VideoCaptureRepository {
    val frames: CameraFrameFlow
}
