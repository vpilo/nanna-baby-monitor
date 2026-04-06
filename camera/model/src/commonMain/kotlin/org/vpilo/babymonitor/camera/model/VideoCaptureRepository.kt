package org.vpilo.babymonitor.camera.model

import org.vpilo.babymonitor.model.CameraFrameFlow

/**
 * Repository for raw captured camera frames.
 * This repository provides uncompressed camera frames, as captured by a webcam or camera.
 */
interface VideoCaptureRepository {
    val frames: CameraFrameFlow
}
