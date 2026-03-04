package org.vpilo.babymonitor.camera.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.camera.model.CameraRepository
import org.vpilo.babymonitor.camera.model.CameraFrameData

class CameraViewModel(
    private val cameraRepository: CameraRepository,
) : ViewModel() {

    val frames: SharedFlow<CameraFrameData> = cameraRepository.frames

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                cameraRepository.start()
            } else {
                cameraRepository.stop()
            }
        }
    }
}
