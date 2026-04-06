package org.vpilo.babymonitor.camera.data

import org.vpilo.babymonitor.camera.model.VideoCaptureRepository
import org.vpilo.babymonitor.model.CameraFrameFlow

class CameraVideoCaptureRepository : VideoCaptureRepository {
    private val dataSource: VideoCaptureDataSource = VideoCaptureDataSource()

    override val frames: CameraFrameFlow = dataSource.frames
}
