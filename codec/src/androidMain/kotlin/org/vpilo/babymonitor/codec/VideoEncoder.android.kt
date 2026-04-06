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

@Suppress("LoopWithTooManyJumpStatements", "ComplexCondition")
actual class VideoEncoder actual constructor(
    private val input: CameraFrameFlow,
    private val output: MutableStreamingVideoFlow,
    coroutineContext: CoroutineContext,
) {
    private val coroutineScope = CoroutineScope(coroutineContext)

    private var encoder: MediaCodec? = null
    private var videoEncodeJob: Job? = null
    private var videoFrameCount = 0L

    /** Stride (in bytes) the encoder expects for the Y plane. */
    private var encoderStride = 0

    /** Vertical stride (slice height) the encoder uses before the UV plane starts. */
    private var encoderSliceHeight = 0

    /** SPS/PPS bytes emitted by the encoder as BUFFER_FLAG_CODEC_CONFIG. */
    private var codecConfigData: ByteArray? = null

    actual fun start() {
        if (videoEncodeJob?.isActive == true) return

        videoEncodeJob =
            coroutineScope.launch {
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
                            encoder = codec
                            videoFrameCount = 0
                            codecConfigData = null
                            Logger.d(TAG) {
                                "Video encoder configured for ${configuredWidth}x$configuredHeight" +
                                    ", stride=$encoderStride, sliceHeight=$encoderSliceHeight"
                            }
                        }

                        encodeVideoFrame(codec, frame)
                    }
                } finally {
                    codec?.let { releaseCodec(it) }
                    encoder = null
                }
            }
    }

    actual fun stop() {
        videoEncodeJob?.cancel()
        videoEncodeJob = null
    }

    private fun createVideoEncoder(
        width: Int,
        height: Int,
    ): MediaCodec {
        val format =
            MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, MediaFormats.Video.BIT_RATE)
                setInteger(MediaFormat.KEY_FRAME_RATE, MediaFormats.Video.FRAME_RATE)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, MediaFormats.Video.KEY_FRAME_INTERVAL_SECONDS)
                // NV12 (semi-planar): matches the tightly-packed NV12 bytes from capture
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
                )
            }
        return MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).also {
            it.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            it.start()

            // Query the actual stride / slice-height the encoder expects.
            // These may differ from width / height due to hardware alignment.
            val inputFormat = it.inputFormat
            encoderStride =
                if (inputFormat.containsKey(MediaFormat.KEY_STRIDE)) {
                    inputFormat.getInteger(MediaFormat.KEY_STRIDE)
                } else {
                    width
                }
            encoderSliceHeight =
                if (inputFormat.containsKey(MediaFormat.KEY_SLICE_HEIGHT)) {
                    inputFormat.getInteger(MediaFormat.KEY_SLICE_HEIGHT)
                } else {
                    height
                }
            if (encoderStride < width) encoderStride = width
            if (encoderSliceHeight < height) encoderSliceHeight = height
        }
    }

    private fun encodeVideoFrame(
        codec: MediaCodec?,
        frame: CameraFrame,
    ) {
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

            val w = frame.width
            val h = frame.height

            if (encoderStride == w && encoderSliceHeight == h) {
                // No padding needed — copy tightly-packed NV12 directly
                val size = minOf(frame.bytes.size, inputBuffer.remaining())
                inputBuffer.put(frame.bytes, 0, size)
            } else {
                // Copy Y plane row-by-row with stride padding
                val src = frame.bytes
                for (row in 0 until h) {
                    inputBuffer.position(row * encoderStride)
                    inputBuffer.put(src, row * w, w)
                }
                // Copy UV plane row-by-row with stride padding.
                // UV plane starts at encoderStride * encoderSliceHeight in the buffer.
                val uvSrcOffset = w * h
                val uvDstOffset = encoderStride * encoderSliceHeight
                val uvHeight = h / 2
                for (row in 0 until uvHeight) {
                    inputBuffer.position(uvDstOffset + row * encoderStride)
                    inputBuffer.put(src, uvSrcOffset + row * w, w)
                }
            }

            // Use the buffer's actual capacity — not the computed stride×sliceHeight total,
            // which may exceed the buffer size on some hardware.
            codec.queueInputBuffer(inputIndex, 0, inputBuffer.capacity(), presentationTimeUs, 0)
        }

        drainEncoder(codec)
    }

    private fun drainEncoder(codec: MediaCodec) {
        val bufferInfo = MediaCodec.BufferInfo()
        while (true) {
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)
            if (outputIndex < 0) break

            val outputBuffer: ByteBuffer? = codec.getOutputBuffer(outputIndex)
            if (outputBuffer == null || bufferInfo.size <= 0) {
                codec.releaseOutputBuffer(outputIndex, false)
                continue
            }

            val data = ByteArray(bufferInfo.size)
            outputBuffer.position(bufferInfo.offset)
            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
            outputBuffer.get(data)

            val isKey = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
            val isCodecConfig = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0

            if (isCodecConfig) {
                // Store SPS/PPS — don't emit as a separate chunk
                codecConfigData = ensureAnnexB(data)
            } else {
                val annexBData = ensureAnnexB(data)
                // Prepend SPS/PPS to every keyframe so the decoder can always start
                val emitData =
                    if (isKey && codecConfigData != null) {
                        codecConfigData!! + annexBData
                    } else {
                        annexBData
                    }

                output.tryEmit(
                    EncodedVideoStreamChunk(
                        data = emitData,
                        isKeyFrame = isKey,
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
        } catch (ex: IllegalStateException) {
            Logger.w(TAG, ex) { "Error releasing codec" }
        }
    }

    private companion object {
        private val TAG = VideoEncoder::class

        const val CODEC_TIMEOUT_US = 10_000L

        private val ANNEX_B_START_CODE = byteArrayOf(0x00, 0x00, 0x00, 0x01)

        private val keyframeRequest =
            Bundle().apply {
                putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
            }

        /**
         * Ensures the NAL unit data uses Annex-B start codes (0x00000001).
         * Some devices emit AVCC length-prefixed NALUs; this converts them.
         */
        private fun ensureAnnexB(data: ByteArray): ByteArray {
            if (data.size >= 4 &&
                data[0] == byteFalse &&
                data[1] == byteFalse &&
                (data[2] == byteTrue || data[3] == byteTrue)
            ) {
                // Already Annex-B
                return data
            }

            // Convert AVCC (4-byte big-endian length prefix) → Annex-B
            val result = java.io.ByteArrayOutputStream(data.size + 16)
            var offset = 0
            while (offset + 4 <= data.size) {
                val nalLen =
                    ((data[offset].toInt() and 0xFF) shl 24) or
                        ((data[offset + 1].toInt() and 0xFF) shl 16) or
                        ((data[offset + 2].toInt() and 0xFF) shl 8) or
                        (data[offset + 3].toInt() and 0xFF)
                offset += 4
                if (nalLen <= 0 || offset + nalLen > data.size) break
                result.write(ANNEX_B_START_CODE)
                result.write(data, offset, nalLen)
                offset += nalLen
            }
            val converted = result.toByteArray()
            return if (converted.isNotEmpty()) converted else data
        }
    }
}
