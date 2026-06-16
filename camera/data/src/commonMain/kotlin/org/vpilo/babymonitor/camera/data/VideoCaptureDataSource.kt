package org.vpilo.babymonitor.camera.data

import org.vpilo.babymonitor.camera.model.CameraResolution
import org.vpilo.babymonitor.model.OpaqueVideoStream

internal expect class VideoCaptureDataSource() {
    val videoStream: OpaqueVideoStream

    fun setResolution(resolution: CameraResolution)

    fun setLowLightBoostEnabled(enabled: Boolean)
}
