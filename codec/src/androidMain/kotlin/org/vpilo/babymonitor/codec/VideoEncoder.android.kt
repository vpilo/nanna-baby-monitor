package org.vpilo.babymonitor.codec

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.CameraFrame
import org.vpilo.babymonitor.model.CameraFrameFlow
import org.vpilo.babymonitor.model.EncodedVideoStreamChunk
import org.vpilo.babymonitor.model.MediaFormats
import org.vpilo.babymonitor.model.MutableStreamingVideoFlow
import java.nio.ByteBuffer
import kotlin.coroutines.CoroutineContext

actual class VideoEncoder actual constructor(
    private val input: CameraFrameFlow,
    private val output: MutableStreamingVideoFlow,
    coroutineContext: CoroutineContext,
) {
    private val coroutineScope = CoroutineScope(coroutineContext)

    private var videoEncoder: MediaCodec? = null
    private var videoEncodeJob: Job? = null
    private var videoFrameCount = 0L

    /** SPS/PPS bytes emitted by the encoder as BUFFER_FLAG_CODEC_CONFIG. */
    private var codecConfigData: ByteArray? = null

    actual fun start() {
        if (videoEncodeJob?.isActive == true) return

        videoEncodeJob = coroutineScope.launch {
            var codec: MediaCodec? = null
            var configuredWidth = 0
            var configuredHeight = 0

            try {
                input.collect { frame ->
                    if (!isActive) return@collect

                    // (Re)create encoder if resolution changed
                    if (codec == null || frame.width != configuredWidth || frame.height != configuredHeight) {
                        codec?.let { releaseCodec(it) }
                        configuredWidth = frame.width
                        configuredHeight = frame.height
                        codec = createVideoEncoder(configuredWidth, configuredHeight)
                        videoEncoder = codec
                        videoFrameCount = 0
                        codecConfigData = null
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

    actual fun stop() {
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

    private fun encodeVideoFrame(codec: MediaCodec?, frame: CameraFrame) {
        if (codec == null) return
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

                val isKey = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
                val isCodecConfig = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0

                if (isCodecConfig) {
                    // Store SPS/PPS — don't emit as a separate chunk
                    codecConfigData = data.copyOf()
                } else {
                    // Prepend SPS/PPS to every keyframe so the decoder can always start
                    val emitData = if (isKey && codecConfigData != null) {
                        codecConfigData!! + data
                    } else {
                        data
                    }

                    output.tryEmit(
                        EncodedVideoStreamChunk(
                            data = emitData,
                            isKeyFrame = isKey,
                        ),
                    )
                }
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

    private companion object {
        private val TAG = VideoEncoder::class

        const val CODEC_TIMEOUT_US = 10_000L

        private val keyframeRequest = Bundle().apply {
            putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
        }
    }
}
