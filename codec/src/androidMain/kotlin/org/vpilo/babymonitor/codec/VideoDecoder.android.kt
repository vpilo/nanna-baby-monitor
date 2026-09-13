package org.vpilo.babymonitor.codec

import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.AndroidClientVideoStream
import org.vpilo.babymonitor.model.EncodedVideoStreamChunk
import org.vpilo.babymonitor.model.MediaFormats
import org.vpilo.babymonitor.model.OpaqueVideoStream
import org.vpilo.babymonitor.model.StreamingVideoFlow
import java.nio.ByteBuffer
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Suppress("NestedBlockDepth", "LoopWithTooManyJumpStatements")
actual class VideoDecoder actual constructor(
    private val input: StreamingVideoFlow,
    coroutineContext: CoroutineContext,
) {
    private val coroutineScope = CoroutineScope(coroutineContext)

    private var decodeJob: Job? = null

    private val mutableVideoStream = AndroidClientVideoStream()

    actual val videoStream: OpaqueVideoStream = mutableVideoStream

    actual fun start() {
        if (decodeJob?.isActive == true) {
            Logger.w(TAG) { "Video decoder is already running, ignoring start request." }
            return
        }

        decodeJob =
            coroutineScope.launch {
                mutableVideoStream.surface.collectLatest { surface ->
                    if (surface == null || !isActive) return@collectLatest
                    while (true) {
                        try {
                            runDecodingSession(surface)
                        } catch (ex: CancellationException) {
                            throw ex
                        } catch (ex: IllegalStateException) {
                            Logger.e(TAG, ex) { "Video decoder failed, restarting" }
                        }
                        delay(RESTART_DELAY)
                    }
                }
            }
    }

    actual fun stop() {
        decodeJob?.cancel()
        decodeJob = null
    }

    private suspend fun runDecodingSession(surface: Surface) {
        var codec: MediaCodec? = null
        try {
            input.collect { chunk ->
                if (codec == null && chunk.isKeyFrame) {
                    // Initial size hint; the actual resolution is determined
                    // by the SPS/PPS in the bitstream and will be reported
                    // via INFO_OUTPUT_FORMAT_CHANGED.
                    val format =
                        MediaFormat.createVideoFormat(
                            MediaFormat.MIMETYPE_VIDEO_AVC,
                            MediaFormats.Video.ENCODE_WIDTH,
                            MediaFormats.Video.ENCODE_HEIGHT,
                        )
                    // Extract SPS and PPS NAL units from the keyframe data
                    // and set them as codec-specific data so the decoder is
                    // fully initialized before it receives any frames.
                    extractCodecSpecificData(chunk.data)
                        ?.also {
                            format.setByteBuffer("csd-0", ByteBuffer.wrap(it))
                        }
                    codec =
                        MediaCodec
                            .createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
                            .also {
                                it.configure(format, surface, null, 0)
                                it.start()
                            }
                    Logger.d(TAG) { "Video decoder started" }
                }

                mutableVideoStream.setRotation(chunk.rotation)

                codec?.let { decodeFrame(it, chunk) }
            }
        } finally {
            codec?.let {
                releaseCodec(it)
                Logger.d(TAG) { "Video decoder stopped" }
            }
        }
    }

    private fun decodeFrame(
        codec: MediaCodec,
        chunk: EncodedVideoStreamChunk,
    ) {
        // Feed encoded data
        val inputIndex = codec.dequeueInputBuffer(INPUT_TIMEOUT_US)
        if (inputIndex >= 0) {
            val inputBuffer = codec.getInputBuffer(inputIndex) ?: return
            inputBuffer.clear()
            val size = minOf(chunk.data.size, inputBuffer.remaining())
            inputBuffer.put(chunk.data, 0, size)
            val flags = if (chunk.isKeyFrame) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
            // PTS is not used when rendering to a Surface; pass 0.
            codec.queueInputBuffer(inputIndex, 0, size, 0L, flags)
        } else {
            Logger.d(TAG) { "No free input buffer for chunk (key=${chunk.isKeyFrame}, size=${chunk.data.size})" }
        }

        // Drain all available decoded frames without blocking
        val bufferInfo = MediaCodec.BufferInfo()
        while (true) {
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0L)

            if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val format = codec.outputFormat
                val width = format.getInteger(MediaFormat.KEY_WIDTH)
                val height = format.getInteger(MediaFormat.KEY_HEIGHT)
                mutableVideoStream.setFrameSize(width, height)
                Logger.d(TAG) { "Output format changed: ${width}x$height" }
                continue
            }

            if (outputIndex < 0) break

            try {
                val render = bufferInfo.size > 0
                codec.releaseOutputBuffer(outputIndex, render)
                if (render) {
                    mutableVideoStream.signalFrameRendered()
                } else {
                    Logger.d(TAG) { "Skipped non-render output buffer (size=${bufferInfo.size}, flags=${bufferInfo.flags})" }
                }
            } catch (ex: IllegalStateException) {
                Logger.w(TAG, ex) { "Failed to release decoded output buffer" }
                break
            }
        }
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
        while (i < data.size - 2) {
            if (data[i] == byteFalse && data[i + 1] == byteFalse) {
                if (i + 3 < data.size && data[i + 2] == byteFalse && data[i + 3] == byteTrue) {
                    startPositions.add(Pair(i, 4))
                    i += 4
                } else if (data[i + 2] == byteTrue) {
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
        } catch (ex: IllegalStateException) {
            Logger.w(TAG, ex) { "Error releasing decoder" }
        }
    }

    private companion object {
        private val TAG = VideoDecoder::class

        /** Timeout when waiting for a free input buffer to submit encoded data. */
        const val INPUT_TIMEOUT_US = 10_000L

        /** Delay before retrying to build the codec on failure. */
        val RESTART_DELAY: Duration = 2.seconds
    }
}
