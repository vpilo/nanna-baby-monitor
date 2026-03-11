package org.vpilo.babymonitor.codec

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
import org.vpilo.babymonitor.model.CameraFrame
import org.vpilo.babymonitor.model.VideoCaptureRepository
import org.vpilo.babymonitor.model.MediaFormats
import org.vpilo.babymonitor.model.EncodedVideoStreamChunk
import org.vpilo.babymonitor.model.StreamingVideoFlow
import org.vpilo.babymonitor.model.repository.StreamingVideoRepository
import org.vpilo.babymonitor.model.repository.SharedResourceRepository
import java.nio.ByteBuffer

actual class PlatformVideoEncoderRepository(
    private val videoCaptureRepository: VideoCaptureRepository,
) : StreamingVideoRepository,
    SharedResourceRepository<EncodedVideoStreamChunk>(
        bufferCapacity = MediaFormats.BufferSizes.MAX_VIDEO_STREAM_BUFFER_SIZE,
    ), AndroidService {

    override val chunks: StreamingVideoFlow = collector.asSharedFlow()

    private var videoEncoder: MediaCodec? = null
    private var videoEncodeJob: Job? = null
    private var videoFrameCount = 0L

    override fun onServiceStarted(context: Context, lifecycleOwner: LifecycleOwner) {
        if (videoEncodeJob?.isActive == true) return

        videoEncodeJob = coroutineScope.launch {
            var codec: MediaCodec? = null
            var configuredWidth = 0
            var configuredHeight = 0

            try {
                videoCaptureRepository.frames.collect { frame ->
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

                    encodeVideoFrame(codec, frame)
                }
            } finally {
                codec?.let { releaseCodec(it) }
                videoEncoder = null
            }
        }
    }

    override fun onServiceStopped() {
        videoEncodeJob?.cancel()
        videoEncodeJob = null
    }

    private fun createVideoEncoder(width: Int, height: Int): MediaCodec {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, MediaFormats.Video.BIT_RATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, MediaFormats.Video.FRAME_RATE)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, MediaFormats.Video.KEY_FRAME_INTERVAL_SECONDS)
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
        val presentationTimeUs = videoFrameCount * 1_000_000L / MediaFormats.Video.FRAME_RATE
        videoFrameCount++

        // Request keyframe periodically to let clients connect at any time
        if (videoFrameCount % (MediaFormats.Video.FRAME_RATE * MediaFormats.Video.KEY_FRAME_INTERVAL_SECONDS) == 0L) {
            codec.setParameters(keyframeRequest)
        }

        val inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
        if (inputIndex >= 0) {
            val inputBuffer = codec.getInputBuffer(inputIndex) ?: return
            inputBuffer.clear()
            val size = minOf(frame.bytes.size, inputBuffer.remaining())
            inputBuffer.put(frame.bytes, 0, size)
            codec.queueInputBuffer(inputIndex, 0, size, presentationTimeUs, 0)
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
                val isKey = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0

                collector.tryEmit(
                    EncodedVideoStreamChunk(
                        data = data,
                        isKeyFrame = isKey,
                        isCodecConfig = isConfig,
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

    override fun start() {
        AndroidServiceRegistry.register(this)
    }

    override fun stop() {
        AndroidServiceRegistry.unregister(this)
    }

    override val TAG = PlatformVideoEncoderRepository::class

    private companion object {
        const val CODEC_TIMEOUT_US = 10_000L

        private val keyframeRequest = Bundle().apply {
            putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
        }
    }
}
