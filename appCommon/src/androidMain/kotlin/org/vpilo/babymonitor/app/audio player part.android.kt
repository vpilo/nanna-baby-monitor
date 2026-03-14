package org.vpilo.babymonitor.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaFormat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.android.service.AndroidService
import org.vpilo.babymonitor.android.service.AndroidServiceRegistry
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.AudioFrameFlow
import org.vpilo.babymonitor.model.MediaFormats
import org.vpilo.babymonitor.model.repository.StreamingAudioReceiverRepository
import java.nio.ByteBuffer
import kotlin.coroutines.CoroutineContext

actual class AudioPlayer actual constructor(
    private val input: AudioFrameFlow,
    coroutineContext: CoroutineContext,
) : AndroidService {

    override fun onServiceStarted(context: Context, lifecycleOwner: LifecycleOwner) {
    }

    override fun onServiceStopped() {
    }

/*
    private var decodeJob: Job? = null
    private var audioTrack: AudioTrack? = null


    private fun createAudioDecoder(): MediaCodec {
        val format = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_OPUS,
            MediaFormats.Audio.SAMPLE_RATE,
            MediaFormats.Audio.CHANNELS,
        )
        return MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS).also {
            it.configure(format, null, null, 0)
            it.start()
        }
    }

    private fun createAudioTrack(): AudioTrack {

        val minBufferSize = AudioTrack.getMinBufferSize(
            MediaFormats.Audio.SAMPLE_RATE,
            channelConfig,
            AudioFormat.ENCODING_PCM_16BIT,
        )

        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(MediaFormats.Audio.SAMPLE_RATE)
                    .setChannelMask(channelConfig)
                    .build(),
            )
            .setBufferSizeInBytes(minBufferSize * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    private fun decodeAndPlay(codec: MediaCodec, track: AudioTrack, opusData: ByteArray) {
        // Feed encoded data
        val inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
        if (inputIndex >= 0) {
            val inputBuffer = codec.getInputBuffer(inputIndex) ?: return
            inputBuffer.clear()
            val size = minOf(opusData.size, inputBuffer.remaining())
            inputBuffer.put(opusData, 0, size)
            codec.queueInputBuffer(inputIndex, 0, size, 0, 0)
        }

        // Drain decoded PCM
        val bufferInfo = MediaCodec.BufferInfo()
        while (true) {
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)
            if (outputIndex < 0) break

            try {
                if (bufferInfo.size < 1) continue
                val outputBuffer: ByteBuffer = codec.getOutputBuffer(outputIndex) ?: continue
                outputBuffer.position(bufferInfo.offset)
                outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                val pcmBytes = ByteArray(bufferInfo.size)
                outputBuffer.get(pcmBytes)
                collector.tryEmit(pcmBytes)

                track.write(pcmBytes, 0, pcmBytes.size)
            } finally {
                codec.releaseOutputBuffer(outputIndex, false)
            }
        }
    }

    private fun releaseCodec(codec: MediaCodec) {
        try {
            codec.stop()
            codec.release()
        } catch (ex: Exception) {
            Logger.w(TAG, ex) { "Error releasing decoder" }
        }
    }

    private fun releaseAudioTrack(track: AudioTrack) {
        try {
            track.stop()
            track.release()
        } catch (ex: Exception) {
            Logger.w(TAG, ex) { "Error releasing AudioTrack" }
        }
    }

    override fun start() {
        AndroidServiceRegistry.register(this)
    }

    override fun stop() {
        AndroidServiceRegistry.unregister(this)
    }

    override val TAG = NetworkAudioReceiverRepository::class

    private companion object {
        private const val CODEC_TIMEOUT_US = 10_000L

        private val channelConfig = when (MediaFormats.Audio.CHANNELS) {
            1 -> AudioFormat.CHANNEL_OUT_MONO
            else -> AudioFormat.CHANNEL_OUT_STEREO
        }
    }
*/
}
