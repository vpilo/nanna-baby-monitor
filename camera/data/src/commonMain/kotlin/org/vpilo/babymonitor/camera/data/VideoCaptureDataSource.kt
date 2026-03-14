package org.vpilo.babymonitor.camera.data

import org.vpilo.babymonitor.model.CameraFrameFlow

internal expect class VideoCaptureDataSource() {
    val frames: CameraFrameFlow
}
