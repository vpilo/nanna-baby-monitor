package org.vpilo.babymonitor.camera.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.model.CameraFrameData
import org.vpilo.babymonitor.model.VideoFeedRepository

class CameraViewModel(
    private val videoFeedRepository: VideoFeedRepository,
) : ViewModel() {

    val frames: SharedFlow<CameraFrameData> = videoFeedRepository.frames

    val isEnabled: Boolean
        get() = videoFeedRepository.isOpen

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                videoFeedRepository.start()
            } else {
                videoFeedRepository.close()
            }
        }
    }
}
