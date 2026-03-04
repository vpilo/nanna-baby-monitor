package org.vpilo.babymonitor.camera.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.camera.model.CameraFrameData
import org.vpilo.babymonitor.camera.model.CameraRepository

class CameraViewModel(
    private val cameraRepository: CameraRepository,
) : ViewModel() {

    val frames: SharedFlow<CameraFrameData> = cameraRepository.frames

    val isEnabled: Boolean
        get() = cameraRepository.isStarted()

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
