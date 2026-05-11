package org.vpilo.babymonitor.camera.model

import org.vpilo.babymonitor.model.OpaqueVideoStream
import org.vpilo.babymonitor.model.VideoStream

/**
 * Repository for the camera capture pipeline.
 * Exposes a [VideoStream] consumed by both the viewfinder and the encoder.
 */
interface VideoCaptureRepository {
    val videoStream: OpaqueVideoStream
}
