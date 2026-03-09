package org.vpilo.babymonitor.camera.data

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Bundle
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.android.service.AndroidService
import org.vpilo.babymonitor.android.service.AndroidServiceRegistry
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.AudioChunkRepository
import org.vpilo.babymonitor.model.CameraFrame
import org.vpilo.babymonitor.model.CameraFrameRepository
import org.vpilo.babymonitor.model.Configuration
import org.vpilo.babymonitor.model.EncodedStreamChunk
import org.vpilo.babymonitor.model.VideoFeedFlow
import org.vpilo.babymonitor.model.VideoFeedRepository
import java.nio.ByteBuffer
import kotlin.reflect.KClass

actual class VideoEncoderRepository(
    private val cameraFrameRepository: CameraFrameRepository,
    private val audioChunkRepository: AudioChunkRepository,
) : VideoFeedRepository,
    SharedResourceRepository<EncodedStreamChunk>(
        bufferCapacity = Configuration.MAX_VIDEO_STREAM_BUFFER_SIZE,
    ), AndroidService {

    override val chunks: VideoFeedFlow = collector.asSharedFlow()

    private var videoEncoder: MediaCodec? = null
    private var videoEncodeJob: Job? = null
    private var audioEncoder: MediaCodec? = null
    private var audioEncodeJob: Job? = null
    private var videoFrameCount = 0L

    override fun onServiceStarted(context: Context, lifecycleOwner: LifecycleOwner) {
        Logger.d(TAG) { "Service started, starting encoding" }
        startVideoEncoding()
        startAudioEncoding()
    }

    override fun onServiceStopped() {
        Logger.d(TAG) { "Service stopped, stopping encoding" }
        stopVideoEncoding()
        stopAudioEncoding()
    }

    override fun start() {
        AndroidServiceRegistry.register(this)
    }

    override fun stop() {
        AndroidServiceRegistry.unregister(this)
    }

    // ── Video encoding ─────────────────────────────────────────────

    private fun startVideoEncoding() {
        if (videoEncodeJob?.isActive == true) return

        videoEncodeJob = coroutineScope.launch {
            var codec: MediaCodec? = null
            var configuredWidth = 0
            var configuredHeight = 0

            try {
                cameraFrameRepository.frames.collect { frame ->
                    if (!isActive) return@collect

                    // (Re)create encoder if resolution changed
                    if (codec == null || frame.width != configuredWidth || frame.height != configuredHeight) {
                        codec?.let { releaseCodec(it) }
                        configuredWidth = frame.width
                        configuredHeight = frame.height
                        codec = createVideoEncoder(configuredWidth, configuredHeight)
                        videoEncoder = codec
                        videoFrameCount = 0
                        Logger.d(TAG) { "Video encoder configured for ${configuredWidth}x${configuredHeight}" }
                    }

                    codec?.let { encodeVideoFrame(it, frame) }
                }
            } finally {
                codec?.let { releaseCodec(it) }
                videoEncoder = null
            }
        }
    }

    private fun stopVideoEncoding() {
        videoEncodeJob?.cancel()
        videoEncodeJob = null
    }

    private fun createVideoEncoder(width: Int, height: Int): MediaCodec {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BIT_RATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, VIDEO_FRAME_RATE)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, KEY_FRAME_INTERVAL_SEC)
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible,
            )
        }
        return MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).also {
            it.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            it.start()
        }
    }

    private fun encodeVideoFrame(codec: MediaCodec, frame: CameraFrame) {
        val presentationTimeUs = videoFrameCount * 1_000_000L / VIDEO_FRAME_RATE
        videoFrameCount++

        // Request keyframe periodically for late joiners
        if (videoFrameCount % (VIDEO_FRAME_RATE * KEY_FRAME_INTERVAL_SEC) == 0L) {
            val params = Bundle().apply {
                putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
            }
            try {
                codec.setParameters(params)
            } catch (_: Exception) {
                // Not all devices support this
            }
        }

        // Feed input — frame.bytes is already NV21 from CameraRepository
        val inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
        if (inputIndex >= 0) {
            val inputBuffer = codec.getInputBuffer(inputIndex) ?: return
            inputBuffer.clear()
            val size = minOf(frame.bytes.size, inputBuffer.remaining())
            inputBuffer.put(frame.bytes, 0, size)
            codec.queueInputBuffer(inputIndex, 0, size, presentationTimeUs, 0)
        }

        drainEncoder(codec, isAudio = false)
    }

    // ── Audio encoding ─────────────────────────────────────────────

    private fun startAudioEncoding() {
        if (audioEncodeJob?.isActive == true) return

        audioEncodeJob = coroutineScope.launch {
            val codec = createAudioEncoder()
            audioEncoder = codec
            Logger.d(TAG) { "Audio encoder started" }

            try {
                audioChunkRepository.samples.collect { pcmChunk ->
                    if (!isActive) return@collect
                    encodeAudioChunk(codec, pcmChunk)
                }
            } finally {
                releaseCodec(codec)
                audioEncoder = null
            }
        }
    }

    private fun stopAudioEncoding() {
        audioEncodeJob?.cancel()
        audioEncodeJob = null
    }

    private fun createAudioEncoder(): MediaCodec {
        val format = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC,
            AUDIO_SAMPLE_RATE,
            AUDIO_CHANNELS,
        ).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BIT_RATE)
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        }
        return MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).also {
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

        drainEncoder(codec, isAudio = true)
    }

    // ── Shared ──────────────────────────────────────────────────────

    private fun drainEncoder(codec: MediaCodec, isAudio: Boolean) {
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
                val isKey = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0

                collector.tryEmit(
                    EncodedStreamChunk(
                        data = data,
                        isKeyFrame = isKey,
                        isCodecConfig = isConfig,
                        isAudio = isAudio,
                    )
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

    override val TAG: KClass<*> = VideoEncoderRepository::class

    private companion object {
        const val VIDEO_BIT_RATE = 2_000_000 // 2 Mbps
        const val VIDEO_FRAME_RATE = 30
        const val KEY_FRAME_INTERVAL_SEC = 2
        const val CODEC_TIMEOUT_US = 10_000L

        const val AUDIO_SAMPLE_RATE = 44100
        const val AUDIO_CHANNELS = 1
        const val AUDIO_BIT_RATE = 128_000 // 128 kbps AAC
    }
}
