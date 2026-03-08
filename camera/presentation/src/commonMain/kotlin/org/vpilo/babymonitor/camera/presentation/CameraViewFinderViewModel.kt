package org.vpilo.babymonitor.camera.presentation

import androidx.lifecycle.ViewModel
import org.vpilo.babymonitor.model.CameraFrameFlow
import org.vpilo.babymonitor.model.CameraFrameRepository

class CameraViewFinderViewModel(
    cameraFrameRepository: CameraFrameRepository,
) : ViewModel() {

    val frames: CameraFrameFlow =
        cameraFrameRepository.frames
}
