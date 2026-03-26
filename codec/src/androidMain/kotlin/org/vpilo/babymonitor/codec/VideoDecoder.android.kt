package org.vpilo.babymonitor.codec

import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
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
import org.vpilo.babymonitor.model.MediaFormats
import org.vpilo.babymonitor.model.StreamingVideoFlow
import java.io.ByteArrayOutputStream
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
    private var frameIndex = 0L

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
                            // Initial size hint; the actual resolution is determined
                            // by the SPS/PPS in the bitstream and will be reported
                            // via INFO_OUTPUT_FORMAT_CHANGED.
                            MediaFormats.Video.ENCODE_WIDTH,
                            MediaFormats.Video.ENCODE_HEIGHT,
                        )

                        // Extract SPS and PPS NAL units from the keyframe data
                        // and set them as codec-specific data so the decoder is
                        // fully initialized before it receives any frames.
                        val csd = extractCodecSpecificData(chunk.data)
                        if (csd != null) {
                            format.setByteBuffer("csd-0", ByteBuffer.wrap(csd))
                        }

                        codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).also {
                            it.configure(format, null, null, 0)
                            it.start()
                        }
                        decoder = codec
                        frameIndex = 0
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
        val inputIndex = codec.dequeueInputBuffer(INPUT_TIMEOUT_US)
        if (inputIndex >= 0) {
            val inputBuffer = codec.getInputBuffer(inputIndex) ?: return
            inputBuffer.clear()
            val size = minOf(chunk.data.size, inputBuffer.remaining())
            inputBuffer.put(chunk.data, 0, size)
            val flags = if (chunk.isKeyFrame) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
            val presentationTimeUs = frameIndex * 1_000_000L / MediaFormats.Video.FRAME_RATE
            frameIndex++
            codec.queueInputBuffer(inputIndex, 0, size, presentationTimeUs, flags)
        }

        // Drain all available decoded frames without blocking
        val bufferInfo = MediaCodec.BufferInfo()
        while (true) {
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, OUTPUT_TIMEOUT_US)

            if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                Logger.d(TAG) {
                    with(codec.outputFormat) {
                        "Output format changed: " +
                                "${getInteger(MediaFormat.KEY_WIDTH)}" +
                                "x" +
                                "${getInteger(MediaFormat.KEY_HEIGHT)}"
                    }
                }
                continue
            }

            if (outputIndex < 0) break

            try {
                if (bufferInfo.size > 0) {
                    val image = codec.getOutputImage(outputIndex)
                    if (image != null) {
                        output.tryEmit(image.toBitmap())
                        image.close()
                    }
                }
            } finally {
                codec.releaseOutputBuffer(outputIndex, false)
            }
        }
    }

    /**
     * Converts a YUV_420_888 [Image] to an [ImageBitmap] using Android's
     * hardware-accelerated [YuvImage] JPEG path. This is dramatically faster
     * than a per-pixel Kotlin loop and prevents frame drops that cause
     * pixelation/corruption during fast motion.
     */
    private fun Image.toBitmap(): ImageBitmap {
        val w = width
        val h = height

        val yPlane = planes[0]
        val uPlane = planes[1]
        val vPlane = planes[2]

        val yRowStride = yPlane.rowStride
        val uvRowStride = uPlane.rowStride
        val uvPixelStride = uPlane.pixelStride

        // Build a tightly-packed NV21 byte array (Y plane followed by interleaved VU).
        // NV21 is the format YuvImage supports natively.
        val nv21 = ByteArray(w * h + w * (h / 2))

        // Copy Y plane
        val yBuf = yPlane.buffer
        if (yRowStride == w) {
            yBuf.position(0)
            yBuf.get(nv21, 0, w * h)
        } else {
            for (row in 0 until h) {
                yBuf.position(row * yRowStride)
                yBuf.get(nv21, row * w, w)
            }
        }

        // Copy UV planes into interleaved VU order (NV21)
        val uBuf = uPlane.buffer
        val vBuf = vPlane.buffer
        val uvHeight = h / 2
        val uvWidth = w / 2
        var nv21Offset = w * h

        if (uvPixelStride == 2 && uvRowStride == w) {
            // Semi-planar layout (NV12 or NV21) — the V and U buffers overlap
            // and are already interleaved in VU order at pixelStride=2.
            // Bulk-copy each row of interleaved VU data directly.
            // On the last row the buffer may be 1 byte shorter (no trailing
            // stride padding), so clamp to the number of bytes remaining.
            vBuf.position(0)
            for (row in 0 until uvHeight) {
                vBuf.position(row * uvRowStride)
                val bytesToRead = minOf(w, vBuf.remaining())
                vBuf.get(nv21, nv21Offset, bytesToRead)
                nv21Offset += w
            }
        } else {
            // Generic path: works for any pixel stride / row stride
            for (row in 0 until uvHeight) {
                for (col in 0 until uvWidth) {
                    val uvIdx = row * uvRowStride + col * uvPixelStride
                    nv21[nv21Offset++] = vBuf[uvIdx]
                    nv21[nv21Offset++] = uBuf[uvIdx]
                }
            }
        }

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, w, h, null)
        val jpegStream = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, w, h), 100, jpegStream)
        val jpegBytes = jpegStream.toByteArray()

        return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size).asImageBitmap()
    }

    /**
     * Extracts all SPS (type 7) and PPS (type 8) NAL units from Annex-B data.
     * Returns them concatenated (with their start codes) as a single ByteArray
     * suitable for MediaFormat "csd-0", or null if none were found.
     */
    private fun extractCodecSpecificData(annexBData: ByteArray): ByteArray? {
        val nalUnits = splitNalUnits(annexBData)
        val csdParts = mutableListOf<ByteArray>()

        for ((startCodeLen, offset, length) in nalUnits) {
            if (length <= startCodeLen) continue
            val nalType = annexBData[offset + startCodeLen].toInt() and 0x1F
            // SPS = 7, PPS = 8
            if (nalType == 7 || nalType == 8) {
                csdParts.add(annexBData.copyOfRange(offset, offset + length))
            }
        }

        if (csdParts.isEmpty()) return null
        // Concatenate all SPS/PPS NAL units (each already includes its start code)
        val totalSize = csdParts.sumOf { it.size }
        val result = ByteArray(totalSize)
        var pos = 0
        for (part in csdParts) {
            part.copyInto(result, pos)
            pos += part.size
        }
        return result
    }

    /**
     * Splits Annex-B byte stream into individual NAL units.
     * Returns a list of (startCodeLength, offset, totalLength) triples.
     */
    private fun splitNalUnits(data: ByteArray): List<Triple<Int, Int, Int>> {
        val units = mutableListOf<Triple<Int, Int, Int>>()
        val startPositions = mutableListOf<Pair<Int, Int>>() // (offset, startCodeLen)

        // First pass: find all start code positions
        var i = 0
        val zero = 0x00.toByte()
        val one = 0x01.toByte()
        while (i < data.size - 2) {
            if (data[i] == zero && data[i + 1] == zero) {
                if (i + 3 < data.size && data[i + 2] == zero && data[i + 3] == one) {
                    startPositions.add(Pair(i, 4))
                    i += 4
                } else if (data[i + 2] == one) {
                    startPositions.add(Pair(i, 3))
                    i += 3
                } else {
                    i++
                }
            } else {
                i++
            }
        }

        // Second pass: determine each NAL unit's extent
        for (idx in startPositions.indices) {
            val (offset, startCodeLen) = startPositions[idx]
            val end = if (idx + 1 < startPositions.size) startPositions[idx + 1].first else data.size
            units.add(Triple(startCodeLen, offset, end - offset))
        }

        return units
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

        /** Timeout when waiting for a free input buffer to submit encoded data. */
        const val INPUT_TIMEOUT_US = 10_000L

        /**
         * Timeout when draining decoded output frames. Use 0 (non-blocking) so
         * that the collect-loop is never stalled waiting for the decoder,
         * preventing back-pressure that causes the SharedFlow to drop chunks.
         */
        const val OUTPUT_TIMEOUT_US = 0L
    }
}
