package org.vpilo.babymonitor.codec

import android.content.Context
import android.media.MediaCodec
import android.media.MediaFormat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.android.service.AndroidService
import org.vpilo.babymonitor.android.service.AndroidServiceRegistry
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.AudioCaptureRepository
import org.vpilo.babymonitor.model.MediaFormats
import org.vpilo.babymonitor.model.EncodedAudioStreamChunk
import org.vpilo.babymonitor.model.StreamingAudioFlow
import org.vpilo.babymonitor.model.repository.StreamingAudioSenderRepository
import org.vpilo.babymonitor.model.repository.SharedResourceRepository
import java.nio.ByteBuffer

actual class AudioEncoderRepository(
    private val audioCaptureRepository: AudioCaptureRepository,
) : StreamingAudioSenderRepository,
    SharedResourceRepository<EncodedAudioStreamChunk>(
        bufferCapacity = MediaFormats.BufferSizes.MAX_SAMPLE_BUFFER_SIZE,
    ), AndroidService {

    override val chunks: StreamingAudioFlow = collector.asSharedFlow()

    private var audioEncoder: MediaCodec? = null
    private var audioEncodeJob: Job? = null

    override fun onServiceStarted(context: Context, lifecycleOwner: LifecycleOwner) {
        if (audioEncodeJob?.isActive == true) return

        audioEncodeJob = coroutineScope.launch {
            val codec = createAudioEncoder()
            audioEncoder = codec
            Logger.d(TAG) { "Audio encoder started" }

            try {
                audioCaptureRepository.samples.collect { pcmChunk ->
                    if (!isActive) return@collect
                    encodeAudioChunk(codec, pcmChunk)
                }
            } finally {
                releaseCodec(codec)
                audioEncoder = null
            }
        }
    }

    override fun onServiceStopped() {
        audioEncodeJob?.cancel()
        audioEncodeJob = null
    }

    private fun createAudioEncoder(): MediaCodec {
        val format = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_OPUS,
            MediaFormats.Audio.SAMPLE_RATE,
            MediaFormats.Audio.CHANNELS,
        ).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, MediaFormats.Audio.BIT_RATE)
        }
        return MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS).also {
            it.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            it.start()
        }
    }

    private fun encodeAudioChunk(codec: MediaCodec, pcmData: ByteArray) {
        var offset = 0
        while (offset < pcmData.size) {
            val inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
            if (inputIndex < 0) break

            val inputBuffer = codec.getInputBuffer(inputIndex) ?: break
            inputBuffer.clear()
            val size = minOf(pcmData.size - offset, inputBuffer.remaining())
            inputBuffer.put(pcmData, offset, size)
            codec.queueInputBuffer(inputIndex, 0, size, 0, 0)
            offset += size
        }

        drainEncoder(codec)
    }

    private fun drainEncoder(codec: MediaCodec) {
        val bufferInfo = MediaCodec.BufferInfo()
        while (true) {
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)
            if (outputIndex < 0) break

            val outputBuffer: ByteBuffer = codec.getOutputBuffer(outputIndex) ?: run {
                codec.releaseOutputBuffer(outputIndex, false)
                continue
            }

            if (bufferInfo.size > 0) {
                val data = ByteArray(bufferInfo.size)
                outputBuffer.position(bufferInfo.offset)
                outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                outputBuffer.get(data)

                val isConfig = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0

                collector.tryEmit(
                    EncodedAudioStreamChunk(
                        data = data,
                        isCodecConfig = isConfig,
                    ),
                )
            }

            codec.releaseOutputBuffer(outputIndex, false)
        }
    }

    private fun releaseCodec(codec: MediaCodec) {
        try {
            codec.stop()
            codec.release()
        } catch (e: Exception) {
            Logger.w(TAG, e) { "Error releasing codec" }
        }
    }

    override fun start() {
        AndroidServiceRegistry.register(this)
    }

    override fun stop() {
        AndroidServiceRegistry.unregister(this)
    }

    override val TAG = AudioEncoderRepository::class

    private companion object {
        const val CODEC_TIMEOUT_US = 10_000L
    }
}
