package org.vpilo.babymonitor.codec

import android.graphics.Bitmap
import android.graphics.Bitmap.createBitmap
import android.media.Image
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
                        val format = MediaFormat.createVideoFormat(
                            MediaFormat.MIMETYPE_VIDEO_AVC,
                            DEFAULT_WIDTH,
                            DEFAULT_HEIGHT,
                        )

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
                    val image = codec.getOutputImage(outputIndex)
                    if (image != null) {
                        val bitmap = image.toImageBitmap()
                        output.tryEmit(bitmap)
                        image.close()
                    }
                }
            } finally {
                codec.releaseOutputBuffer(outputIndex, false)
            }
        }
    }

    /**
     * Converts a YUV [Image] from MediaCodec output to an [ImageBitmap].
     *
     * Uses the plane descriptors (row stride, pixel stride) to handle any
     * YUV 420 layout generically: NV12, NV21, I420, or YUV_420_888.
     */
    private fun Image.toImageBitmap(): ImageBitmap {
        val w = width
        val h = height

        val yPlane = planes[0]
        val uPlane = planes[1]
        val vPlane = planes[2]

        val yBuf = yPlane.buffer
        val uBuf = uPlane.buffer
        val vBuf = vPlane.buffer

        val yRowStride = yPlane.rowStride
        val uvRowStride = uPlane.rowStride
        val uvPixelStride = uPlane.pixelStride

        val pixels = IntArray(w * h)

        for (j in 0 until h) {
            for (i in 0 until w) {
                val y = yBuf.get(j * yRowStride + i).toInt() and 0xFF

                val uvRow = j / 2
                val uvCol = i / 2
                val uvIndex = uvRow * uvRowStride + uvCol * uvPixelStride
                val u = (uBuf.get(uvIndex).toInt() and 0xFF) - 128
                val v = (vBuf.get(uvIndex).toInt() and 0xFF) - 128

                // ITU-R BT.601 YUV → RGB
                var r = y + (1370 * v shr 10)
                var g = y - (336 * u + 698 * v shr 10)
                var b = y + (1732 * u shr 10)

                r = r.coerceIn(0, 255)
                g = g.coerceIn(0, 255)
                b = b.coerceIn(0, 255)

                pixels[j * w + i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        return createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, w, 0, 0, w, h)
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
