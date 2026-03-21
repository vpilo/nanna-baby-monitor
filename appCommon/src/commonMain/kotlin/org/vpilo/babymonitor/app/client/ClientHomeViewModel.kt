package org.vpilo.babymonitor.app.client

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.Flow
import org.vpilo.babymonitor.model.AudioFrame
import org.vpilo.babymonitor.model.repository.StreamingAudioReceiverRepository
import org.vpilo.babymonitor.model.repository.StreamingVideoReceiverRepository

class ClientHomeViewModel(
    videoReceiverRepository: StreamingVideoReceiverRepository,
    audioReceiverRepository: StreamingAudioReceiverRepository,
) : ViewModel() {

    val frames: Flow<ImageBitmap> = videoReceiverRepository.decodedFrames

    val audio: Flow<AudioFrame> = audioReceiverRepository.chunks
}
