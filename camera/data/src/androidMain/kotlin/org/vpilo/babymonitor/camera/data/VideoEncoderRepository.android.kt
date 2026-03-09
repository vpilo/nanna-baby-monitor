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
) : VideoFeedRepository,
    SharedResourceRepository<EncodedStreamChunk>(
        bufferCapacity = Configuration.MAX_VIDEO_STREAM_BUFFER_SIZE,
    ), AndroidService {

    override val chunks: VideoFeedFlow = collector.asSharedFlow()

    private var encoder: MediaCodec? = null
    private var encodeJob: Job? = null
    private var frameCount = 0L

    override fun onServiceStarted(context: Context, lifecycleOwner: LifecycleOwner) {
        Logger.d(TAG) { "Service started, waiting for first frame to configure encoder" }
        startEncoding()
    }

    override fun onServiceStopped() {
        Logger.d(TAG) { "Service stopped, stopping encoder" }
        stopEncoding()
    }

    override fun start() {
        AndroidServiceRegistry.register(this)
    }

    override fun stop() {
        AndroidServiceRegistry.unregister(this)
    }

    private fun startEncoding() {
        if (encodeJob?.isActive == true) return

        encodeJob = coroutineScope.launch {
            var codec: MediaCodec? = null
            var configuredWidth = 0
            var configuredHeight = 0

            try {
                cameraFrameRepository.frames.collect { frame ->
                    if (!isActive) return@collect

                    // (Re)create encoder if resolution changed
                    if (codec == null || frame.width != configuredWidth || frame.height != configuredHeight) {
                        codec?.let { releaseEncoder(it) }
                        configuredWidth = frame.width
                        configuredHeight = frame.height
                        codec = createEncoder(configuredWidth, configuredHeight)
                        encoder = codec
                        frameCount = 0
                        Logger.d(TAG) { "Encoder configured for ${configuredWidth}x${configuredHeight}" }
                    }

                    codec?.let { encodeFrame(it, frame) }
                }
            } finally {
                codec?.let { releaseEncoder(it) }
                encoder = null
            }
        }
    }

    private fun stopEncoding() {
        encodeJob?.cancel()
        encodeJob = null
    }

    private fun createEncoder(width: Int, height: Int): MediaCodec {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
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

    private fun releaseEncoder(codec: MediaCodec) {
        try {
            codec.stop()
            codec.release()
        } catch (e: Exception) {
            Logger.w(TAG, e) { "Error releasing encoder" }
        }
    }

    private fun encodeFrame(codec: MediaCodec, frame: CameraFrame) {
        val nv21 = rgbaToNv21(frame.bytes, frame.width, frame.height)
        val presentationTimeUs = frameCount * 1_000_000L / FRAME_RATE
        frameCount++

        // Request keyframe periodically for late joiners
        if (frameCount % (FRAME_RATE * KEY_FRAME_INTERVAL_SEC) == 0L) {
            val params = Bundle().apply {
                putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
            }
            try {
                codec.setParameters(params)
            } catch (_: Exception) {
                // Not all devices support this
            }
        }

        // Feed input
        val inputIndex = codec.dequeueInputBuffer(INPUT_TIMEOUT_US)
        if (inputIndex >= 0) {
            val inputBuffer = codec.getInputBuffer(inputIndex) ?: return
            inputBuffer.clear()
            inputBuffer.put(nv21, 0, minOf(nv21.size, inputBuffer.remaining()))
            codec.queueInputBuffer(inputIndex, 0, nv21.size, presentationTimeUs, 0)
        }

        // Drain output
        drainEncoder(codec)
    }

    private fun drainEncoder(codec: MediaCodec) {
        val bufferInfo = MediaCodec.BufferInfo()
        while (true) {
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, OUTPUT_TIMEOUT_US)
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
                        timestampUs = bufferInfo.presentationTimeUs,
                    )
                )
            }

            codec.releaseOutputBuffer(outputIndex, false)
        }
    }

    override val TAG: KClass<*> = VideoEncoderRepository::class

    private companion object {
        const val BIT_RATE = 2_000_000 // 2 Mbps
        const val FRAME_RATE = 30
        const val KEY_FRAME_INTERVAL_SEC = 2
        const val INPUT_TIMEOUT_US = 10_000L
        const val OUTPUT_TIMEOUT_US = 10_000L

        /**
         * Convert RGBA_8888 pixel data to NV21 (YCrCb 4:2:0 semi-planar).
         * NV21 is the most universally accepted input for Android MediaCodec encoders
         * when using COLOR_FormatYUV420Flexible.
         */
        fun rgbaToNv21(rgba: ByteArray, width: Int, height: Int): ByteArray {
            val frameSize = width * height
            val nv21 = ByteArray(frameSize + frameSize / 2)

            var yIndex = 0
            var uvIndex = frameSize

            for (j in 0 until height) {
                for (i in 0 until width) {
                    val rgbaIndex = (j * width + i) * 4
                    val r = rgba[rgbaIndex].toInt() and 0xFF
                    val g = rgba[rgbaIndex + 1].toInt() and 0xFF
                    val b = rgba[rgbaIndex + 2].toInt() and 0xFF

                    // BT.601 full-range conversion
                    val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                    nv21[yIndex++] = y.coerceIn(0, 255).toByte()

                    // Subsample UV at every 2x2 block
                    if (j % 2 == 0 && i % 2 == 0) {
                        val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                        val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                        // NV21 ordering: V then U
                        nv21[uvIndex++] = v.coerceIn(0, 255).toByte()
                        nv21[uvIndex++] = u.coerceIn(0, 255).toByte()
                    }
                }
            }
            return nv21
        }
    }
}
