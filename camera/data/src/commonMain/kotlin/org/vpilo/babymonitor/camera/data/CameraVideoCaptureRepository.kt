package org.vpilo.babymonitor.camera.data

import org.vpilo.babymonitor.model.CameraFrameFlow
import org.vpilo.babymonitor.camera.model.VideoCaptureRepository

class CameraVideoCaptureRepository : VideoCaptureRepository {
    private val dataSource: VideoCaptureDataSource = VideoCaptureDataSource()

    override val frames: CameraFrameFlow = dataSource.frames
}
