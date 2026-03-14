package org.vpilo.babymonitor.camera.data

import org.vpilo.babymonitor.model.AudioCaptureRepository
import org.vpilo.babymonitor.model.AudioFrameFlow

class MicrophoneAudioCaptureRepository : AudioCaptureRepository {

    private val dataSource: AudioCaptureDataSource = AudioCaptureDataSource()

    override val samples: AudioFrameFlow = dataSource.samples
}
