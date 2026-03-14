package org.vpilo.babymonitor.codec

import android.graphics.Bitmap
import android.graphics.Bitmap.createBitmap
import android.graphics.ImageFormat
import android.media.MediaCodec
import android.media.MediaFormat
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.EncodedVideoStreamChunk
import org.vpilo.babymonitor.model.StreamingVideoFlow
import java.nio.ByteBuffer
import kotlin.coroutines.CoroutineContext

actual class VideoDecoder actual constructor(
    private val input: StreamingVideoFlow,
    private val output: MutableSharedFlow<ImageBitmap>,
    coroutineContext: CoroutineContext,
) {
    private val coroutineScope = CoroutineScope(coroutineContext)

    private var decodeJob: Job? = null
    private var decoder: MediaCodec? = null

    actual fun start() {
        if (decodeJob?.isActive == true) {
            Logger.w(TAG) { "Video decoder is already running, ignoring start request." }
            return
        }

        decodeJob = coroutineScope.launch {
            var codec: MediaCodec? = null

            try {
                input.collect { chunk ->
                    if (!isActive) return@collect

                    // Create decoder on first keyframe
                    if (codec == null && chunk.isKeyFrame) {
                        // Parse width/height from the SPS or use defaults
                        val format = MediaFormat.createVideoFormat(
                            MediaFormat.MIMETYPE_VIDEO_AVC,
                            DEFAULT_WIDTH,
                            DEFAULT_HEIGHT,
                        ).apply {
                            setInteger(MediaFormat.KEY_COLOR_FORMAT, ImageFormat.YUV_420_888)
                        }

                        codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).also {
                            it.configure(format, null, null, 0)
                            it.start()
                        }
                        decoder = codec
                        Logger.d(TAG) { "Video decoder started" }
                    }

                    codec?.let { decodeFrame(it, chunk) }
                }
            } finally {
                codec?.let { releaseCodec(it) }
                decoder = null
            }
        }
    }

    actual fun stop() {
        decodeJob?.cancel()
        decodeJob = null
    }

    private fun decodeFrame(codec: MediaCodec, chunk: EncodedVideoStreamChunk) {
        // Feed encoded data
        val inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
        if (inputIndex >= 0) {
            val inputBuffer = codec.getInputBuffer(inputIndex) ?: return
            inputBuffer.clear()
            val size = minOf(chunk.data.size, inputBuffer.remaining())
            inputBuffer.put(chunk.data, 0, size)
            val flags = if (chunk.isKeyFrame) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
            codec.queueInputBuffer(inputIndex, 0, size, 0, flags)
        }

        // Drain decoded frames
        val bufferInfo = MediaCodec.BufferInfo()
        while (true) {
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)

            if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val outputFormat = codec.outputFormat
                Logger.d(TAG) {
                    "Output format changed: ${outputFormat.getInteger(MediaFormat.KEY_WIDTH)}x${outputFormat.getInteger(MediaFormat.KEY_HEIGHT)}, " +
                            "color=${outputFormat.getInteger(MediaFormat.KEY_COLOR_FORMAT)}"
                }
                continue
            }

            if (outputIndex < 0) break

            try {
                if (bufferInfo.size > 0) {
                    val outputFormat = codec.outputFormat
                    val width = outputFormat.getInteger(MediaFormat.KEY_WIDTH)
                    val height = outputFormat.getInteger(MediaFormat.KEY_HEIGHT)
                    val colorFormat = outputFormat.getInteger(MediaFormat.KEY_COLOR_FORMAT)

                    val outputBuffer = codec.getOutputBuffer(outputIndex) ?: continue
                    outputBuffer.position(bufferInfo.offset)
                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                    val bitmap = yuvToArgbBitmap(outputBuffer, width, height, colorFormat)
                    if (bitmap != null) {
                        output.tryEmit(bitmap)
                    }
                }
            } finally {
                codec.releaseOutputBuffer(outputIndex, false)
            }
        }
    }

    /**
     * Converts YUV data from MediaCodec output to an ARGB ImageBitmap.
     * Handles both NV12 (COLOR_FormatYUV420SemiPlanar = 21) and I420 (COLOR_FormatYUV420Planar = 19).
     */
    private fun yuvToArgbBitmap(
        buffer: ByteBuffer,
        width: Int,
        height: Int,
        colorFormat: Int,
    ): ImageBitmap? {
        val ySize = width * height
        val uvSize = ySize / 4
        val yuvBytes = ByteArray(buffer.remaining())
        buffer.get(yuvBytes)

        val argb = IntArray(ySize)

        for (j in 0 until height) {
            for (i in 0 until width) {
                val yIndex = j * width + i
                val y = (yuvBytes[yIndex].toInt() and 0xFF)

                val uvRow = j / 2
                val uvCol = i / 2
                val u: Int
                val v: Int

                when (colorFormat) {
                    21 -> {
                        // NV12: UV interleaved after Y plane
                        val uvIndex = ySize + uvRow * width + uvCol * 2
                        u = (yuvBytes[uvIndex].toInt() and 0xFF) - 128
                        v = (yuvBytes[uvIndex + 1].toInt() and 0xFF) - 128
                    }

                    19 -> {
                        // I420: U plane then V plane
                        val uIndex = ySize + uvRow * (width / 2) + uvCol
                        val vIndex = ySize + uvSize + uvRow * (width / 2) + uvCol
                        u = (yuvBytes[uIndex].toInt() and 0xFF) - 128
                        v = (yuvBytes[vIndex].toInt() and 0xFF) - 128
                    }

                    else -> {
                        Logger.w(TAG) { "Unsupported color format: $colorFormat" }
                        return null
                    }
                }

                // YUV→RGB BT.601
                val r = (y + 1.402 * v).toInt().coerceIn(0, 255)
                val g = (y - 0.344136 * u - 0.714136 * v).toInt().coerceIn(0, 255)
                val b = (y + 1.772 * u).toInt().coerceIn(0, 255)

                argb[yIndex] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        return createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(argb, 0, width, 0, 0, width, height)
        }.asImageBitmap()
    }

    private fun releaseCodec(codec: MediaCodec) {
        try {
            codec.stop()
            codec.release()
        } catch (e: Exception) {
            Logger.w(TAG, e) { "Error releasing decoder" }
        }
    }

    private companion object {
        private val TAG = VideoDecoder::class

        const val CODEC_TIMEOUT_US = 10_000L
        const val DEFAULT_WIDTH = 640
        const val DEFAULT_HEIGHT = 480
    }
}
