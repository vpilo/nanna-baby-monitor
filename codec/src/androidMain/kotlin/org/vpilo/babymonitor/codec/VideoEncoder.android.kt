package org.vpilo.babymonitor.codec

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Bundle
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.AndroidServerVideoStream
import org.vpilo.babymonitor.model.EncodedVideoStreamChunk
import org.vpilo.babymonitor.model.MediaFormats
import org.vpilo.babymonitor.model.MutableStreamingVideoFlow
import org.vpilo.babymonitor.model.OpaqueVideoStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.coroutines.CoroutineContext

@Suppress("LoopWithTooManyJumpStatements", "ComplexCondition")
actual class VideoEncoder actual constructor(
    source: OpaqueVideoStream,
    private val output: MutableStreamingVideoFlow,
    coroutineContext: CoroutineContext,
) {
    private val coroutineScope = CoroutineScope(coroutineContext)
    private var encodingJob: Job? = null

    private val videoStream: AndroidServerVideoStream =
        checkNotNull(source as? AndroidServerVideoStream) { "Invalid VideoStream" }

    actual fun start() {
        Logger.d(TAG) { "Starting, isActive=${encodingJob?.isActive == true}" }
        if (encodingJob?.isActive == true) {
            Logger.w(TAG) { "Video encoder is already running, ignoring start request." }
            return
        }
        encodingJob =
            coroutineScope.launch {
                videoStream
                    .frameSize
                    .filter { it != IntSize.Zero }
                    .collectLatest { frameSize ->
                        Logger.d(TAG) { "Frame size updated: $frameSize" }
                        runEncodeLoop(frameSize)
                    }
            }
    }

    actual fun stop() {
        Logger.d(TAG) { "Stopping, isActive=${encodingJob?.isActive == true}" }
        encodingJob?.cancel()
        encodingJob = null
    }

    private suspend fun runEncodeLoop(frameSize: IntSize) {
        check(frameSize != IntSize.Zero) { "Source size not set" }
        val width = frameSize.width
        val height = frameSize.height

        Logger.d(TAG) { "Video encoder starting at ${width}x$height" }
        val codec = createVideoEncoder(width, height)
        val inputSurface = codec.createInputSurface()
        codec.start()
        try {
            videoStream.postEncoderSurface(inputSurface)
            drainEncoderLoop(codec, width, height)
        } finally {
            videoStream.postEncoderSurface(null)
            inputSurface.release()
            releaseCodec(codec)
            Logger.d(TAG) { "Video encoder stopped" }
        }
    }

    private suspend fun drainEncoderLoop(
        codec: MediaCodec,
        width: Int,
        height: Int,
    ) {
        var videoFrameCount = 0L
        var codecConfigData: ByteArray? = null
        val bufferInfo = MediaCodec.BufferInfo()
        while (currentCoroutineContext().isActive) {
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)
            if (outputIndex < 0) {
                videoFrameCount++
                if (videoFrameCount % (MediaFormats.Video.FRAME_RATE * MediaFormats.Video.KEY_FRAME_INTERVAL_SECONDS) == 0L) {
                    codec.setParameters(keyframeRequest)
                }
                continue
            }

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
                codecConfigData = ensureAnnexB(data)
            } else {
                val annexBData = ensureAnnexB(data)
                val cachedConfig = codecConfigData
                val emitData =
                    if (isKey && cachedConfig != null) {
                        cachedConfig + annexBData
                    } else {
                        annexBData
                    }
                output.tryEmit(
                    EncodedVideoStreamChunk(
                        data = emitData,
                        isKeyFrame = isKey,
                        rotation = videoStream.rotation.value,
                        frameWidth = width,
                        frameHeight = height,
                    ),
                )
            }

            codec.releaseOutputBuffer(outputIndex, false)
        }
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
                // Surface input mode: COLOR_FormatSurface tells MediaCodec we'll
                // feed it via createInputSurface() — no byte-buffer input.
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface,
                )
            }
        return MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).also {
            it.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
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
                return data
            }

            // Convert AVCC (4-byte big-endian length prefix) to Annex-B
            val result = ByteArrayOutputStream(data.size + 16)
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
