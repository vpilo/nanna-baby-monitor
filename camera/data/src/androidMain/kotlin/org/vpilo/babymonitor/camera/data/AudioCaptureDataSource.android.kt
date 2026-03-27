package org.vpilo.babymonitor.camera.data

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.android.service.AndroidService
import org.vpilo.babymonitor.android.service.AndroidServiceRegistry
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.AudioFrame
import org.vpilo.babymonitor.model.AudioFrameFlow
import org.vpilo.babymonitor.model.MediaFormats
import org.vpilo.babymonitor.model.repository.SharedResourceHolder

internal actual class AudioCaptureDataSource :
    SharedResourceHolder<AudioFrame>(
        bufferCapacity = MediaFormats.BufferSizes.MAX_SAMPLE_BUFFER_SIZE,
    ),
    AndroidService {
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null

    actual val samples: AudioFrameFlow = collector.asSharedFlow()

    override fun onServiceStarted(
        context: Context,
        lifecycleOwner: LifecycleOwner,
    ) {
        Logger.d(TAG) { "Starting mic capture" }
        val audioSource = MediaRecorder.AudioSource.MIC
        val sampleRate = MediaFormats.Audio.SAMPLE_RATE
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        audioRecord = AudioRecord(audioSource, sampleRate, channelConfig, audioFormat, bufferSize)
        recordingJob =
            coroutineScope.launch {
                val buffer = ByteArray(bufferSize)
                audioRecord?.startRecording()
                while (isActive) {
                    audioRecord?.read(buffer, 0, buffer.size)?.let { read ->
                        if (isActive && read > 0) {
                            collector.tryEmit(buffer.copyOf(read))
                        }
                    }
                }
            }
    }

    override fun onServiceStopped() {
        Logger.d(TAG) { "Stopping mic capture" }
        recordingJob?.cancel()
        recordingJob = null
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    override fun start() {
        AndroidServiceRegistry.register(this)
    }

    override fun stop() {
        AndroidServiceRegistry.unregister(this)
    }
}
