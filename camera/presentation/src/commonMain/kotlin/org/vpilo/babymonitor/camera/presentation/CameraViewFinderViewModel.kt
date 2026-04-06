package org.vpilo.babymonitor.camera.presentation

import androidx.lifecycle.ViewModel
import org.vpilo.babymonitor.camera.model.VideoCaptureRepository
import org.vpilo.babymonitor.model.CameraFrameFlow

class CameraViewFinderViewModel(
    videoCaptureRepository: VideoCaptureRepository,
) : ViewModel() {
    val frames: CameraFrameFlow =
        videoCaptureRepository.frames
}
