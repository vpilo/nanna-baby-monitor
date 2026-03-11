package org.vpilo.babymonitor.camera.presentation

import androidx.lifecycle.ViewModel
import org.vpilo.babymonitor.model.CameraFrameFlow
import org.vpilo.babymonitor.model.VideoCaptureRepository

class CameraViewFinderViewModel(
    videoCaptureRepository: VideoCaptureRepository,
) : ViewModel() {

    val frames: CameraFrameFlow =
        videoCaptureRepository.frames
}
