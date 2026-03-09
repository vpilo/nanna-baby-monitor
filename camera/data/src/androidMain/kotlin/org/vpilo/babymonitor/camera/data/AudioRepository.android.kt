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
import org.vpilo.babymonitor.model.AudioChunkRepository
import org.vpilo.babymonitor.model.AudioFlow
import org.vpilo.babymonitor.model.Configuration
import kotlin.reflect.KClass

actual class AudioRepository(
) : AudioChunkRepository,
    SharedResourceRepository<ByteArray>(
        bufferCapacity = Configuration.MAX_SAMPLE_BUFFER_SIZE,
    ), AndroidService {
    private var audioRecord: AudioRecord? = null
    private var audioJob: Job? = null

    override val samples: AudioFlow = collector.asSharedFlow()

    override fun onServiceStarted(context: Context, lifecycleOwner: LifecycleOwner) {
        Logger.d(TAG) { "Starting recording" }
        val audioSource = MediaRecorder.AudioSource.MIC
        val sampleRate = 44100
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        audioRecord = AudioRecord(audioSource, sampleRate, channelConfig, audioFormat, bufferSize)
        audioJob = coroutineScope.launch {
            val buffer = ByteArray(bufferSize)
            audioRecord?.startRecording()
            while (isActive) {
                audioRecord?.read(buffer, 0, buffer.size)?.let { read ->
                    if (isActive && read > 0) {
                        //    previewFeed.tryEmit(buffer)
                    }
                }
            }
        }
    }

    override fun onServiceStopped() {
        Logger.d(TAG) { "Stopping recording" }
        audioJob?.cancel()
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

    override val TAG: KClass<*> = AudioRepository::class
}
