package org.vpilo.babymonitor.codec

import android.media.MediaCodec
import android.media.MediaFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.AudioFrameFlow
import org.vpilo.babymonitor.model.EncodedAudioStreamChunk
import org.vpilo.babymonitor.model.MediaFormats
import org.vpilo.babymonitor.model.MutableStreamingAudioFlow
import java.nio.ByteBuffer
import kotlin.coroutines.CoroutineContext

@Suppress("LoopWithTooManyJumpStatements")
actual class AudioEncoder actual constructor(
    private val input: AudioFrameFlow,
    private val output: MutableStreamingAudioFlow,
    coroutineContext: CoroutineContext,
) {
    private val coroutineScope = CoroutineScope(coroutineContext)

    private var encoder: MediaCodec? = null
    private var audioEncodeJob: Job? = null
    private var presentationTimeUs = 0L

    actual fun start() {
        if (audioEncodeJob?.isActive == true) return

        audioEncodeJob =
            coroutineScope.launch {
                presentationTimeUs = 0L
                val codec = createAudioEncoder()
                encoder = codec
                Logger.d(TAG) { "Audio encoder started" }

                try {
                    input.collect { pcmChunk ->
                        if (!isActive) return@collect
                        encodeAudioChunk(codec, pcmChunk)
                    }
                } catch (ex: MediaCodec.CodecException) {
                    Logger.e(TAG, ex) { "Failed to encode audio; stopping encode loop" }
                    throw CancellationException("Audio codec error", ex)
                } finally {
                    releaseCodec(codec)
                    encoder = null
                }
            }
    }

    actual fun stop() {
        audioEncodeJob?.cancel()
        audioEncodeJob = null
    }

    private fun createAudioEncoder(): MediaCodec {
        val format =
            MediaFormat
                .createAudioFormat(
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

    private fun encodeAudioChunk(
        codec: MediaCodec,
        pcmData: ByteArray,
    ) {
        val bytesPerSample = MediaFormats.Audio.SAMPLE_SIZE_BITS / 8 * MediaFormats.Audio.CHANNELS
        var offset = 0
        while (offset < pcmData.size) {
            val inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
            if (inputIndex < 0) break

            val inputBuffer = codec.getInputBuffer(inputIndex) ?: break
            inputBuffer.clear()
            val size = minOf(pcmData.size - offset, inputBuffer.remaining())
            inputBuffer.put(pcmData, offset, size)

            codec.queueInputBuffer(inputIndex, 0, size, presentationTimeUs, 0)

            // Advance PTS by the duration of the samples queued
            val samplesQueued = size / bytesPerSample
            presentationTimeUs += samplesQueued * 1_000_000L / MediaFormats.Audio.SAMPLE_RATE

            offset += size
        }

        drainEncoder(codec)
    }

    private fun drainEncoder(codec: MediaCodec) {
        val bufferInfo = MediaCodec.BufferInfo()
        while (true) {
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)
            if (outputIndex < 0) break

            val outputBuffer: ByteBuffer =
                codec.getOutputBuffer(outputIndex) ?: run {
                    codec.releaseOutputBuffer(outputIndex, false)
                    continue
                }

            if (bufferInfo.size > 0) {
                val data = ByteArray(bufferInfo.size)
                outputBuffer.position(bufferInfo.offset)
                outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                outputBuffer.get(data)

                output.tryEmit(
                    EncodedAudioStreamChunk(data = data),
                )
            }

            codec.releaseOutputBuffer(outputIndex, false)
        }
    }

    private fun releaseCodec(codec: MediaCodec) {
        try {
            codec.stop()
            codec.release()
        } catch (ex: IllegalStateException) {
            Logger.w(TAG, ex) { "Error releasing codec" }
        }
    }

    private companion object {
        private val TAG = AudioEncoder::class

        const val CODEC_TIMEOUT_US = 10_000L
    }
}
