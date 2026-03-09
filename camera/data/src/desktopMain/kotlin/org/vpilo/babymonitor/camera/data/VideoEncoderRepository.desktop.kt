package org.vpilo.babymonitor.camera.data

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.bytedeco.ffmpeg.avcodec.AVCodecContext
import org.bytedeco.ffmpeg.avcodec.AVPacket
import org.bytedeco.ffmpeg.avutil.AVDictionary
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avcodec.*
import org.bytedeco.ffmpeg.global.avutil.*
import org.bytedeco.ffmpeg.global.swscale.*
import org.bytedeco.ffmpeg.swscale.SwsContext
import org.bytedeco.javacpp.DoublePointer
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.AudioChunkRepository
import org.vpilo.babymonitor.model.CameraFrame
import org.vpilo.babymonitor.model.CameraFrameRepository
import org.vpilo.babymonitor.model.Configuration
import org.vpilo.babymonitor.model.EncodedStreamChunk
import org.vpilo.babymonitor.model.VideoFeedFlow
import org.vpilo.babymonitor.model.VideoFeedRepository
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import java.awt.image.DataBufferInt
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.reflect.KClass

actual class VideoEncoderRepository(
    private val cameraFrameRepository: CameraFrameRepository,
    private val audioChunkRepository: AudioChunkRepository,
) : VideoFeedRepository,
    SharedResourceRepository<EncodedStreamChunk>(
        bufferCapacity = Configuration.MAX_VIDEO_STREAM_BUFFER_SIZE,
    ) {

    override val chunks: VideoFeedFlow = collector.asSharedFlow()

    private var videoEncodeJob: Job? = null
    private var audioEncodeJob: Job? = null

    override fun start() {
        startVideoEncoding()
        startAudioEncoding()
    }

    override fun stop() {
        videoEncodeJob?.cancel()
        videoEncodeJob = null
        audioEncodeJob?.cancel()
        audioEncodeJob = null
    }

    override val TAG: KClass<*> = VideoEncoderRepository::class

    // ── Video encoding ─────────────────────────────────────────────

    private fun startVideoEncoding() {
        if (videoEncodeJob?.isActive == true) {
            Logger.w(TAG) { "Video encoder is already running, ignoring start request." }
            return
        }

        videoEncodeJob = coroutineScope.launch {
            var encoderCtx: VideoEncoderContext? = null
            try {
                cameraFrameRepository.frames.collect { frame ->
                    if (!isActive) return@collect

                    val w = frame.image.width
                    val h = frame.image.height

                    // (Re)create encoder if resolution changed
                    if (encoderCtx == null || encoderCtx!!.width != w || encoderCtx!!.height != h) {
                        encoderCtx?.release()
                        encoderCtx = VideoEncoderContext.create(w, h)
                        Logger.d(TAG) { "Video encoder configured for ${w}x${h}" }
                    }

                    val ctx = encoderCtx!!
                    ctx.encode(frame) { chunk ->
                        collector.tryEmit(chunk)
                    }
                }
            } finally {
                encoderCtx?.release()
            }
        }
    }

    // ── Audio encoding ─────────────────────────────────────────────

    private fun startAudioEncoding() {
        if (audioEncodeJob?.isActive == true) {
            Logger.w(TAG) { "Audio encoder is already running, ignoring start request." }
            return
        }

        audioEncodeJob = coroutineScope.launch {
            val audioCtx = AudioEncoderContext.create()
            Logger.d(TAG) { "Audio encoder started" }

            try {
                audioChunkRepository.samples.collect { pcmChunk ->
                    if (!isActive) return@collect
                    audioCtx.encode(pcmChunk) { chunk ->
                        collector.tryEmit(chunk)
                    }
                }
            } finally {
                audioCtx.release()
            }
        }
    }

    // ── Video encoder context ──────────────────────────────────────

    /**
     * Encapsulates all FFmpeg resources for video encoding at a given resolution.
     */
    private class VideoEncoderContext private constructor(
        val width: Int,
        val height: Int,
        private val codecCtx: AVCodecContext,
        private val swsCtx: SwsContext,
        private val srcFrame: AVFrame,
        private val yuvFrame: AVFrame,
        private val packet: AVPacket,
    ) {
        private var pts = 0L

        fun encode(frame: CameraFrame, emit: (EncodedStreamChunk) -> Unit) {
            fillSourceFrame(frame.image)
            convertToYuv()

            yuvFrame.pts(pts++)

            // Send frame to encoder
            var ret = avcodec_send_frame(codecCtx, yuvFrame)
            if (ret < 0 && ret != AVERROR_EAGAIN()) {
                Logger.w(VideoEncoderContext::class) { "avcodec_send_frame error: $ret" }
                return
            }

            // Receive all available packets
            while (true) {
                ret = avcodec_receive_packet(codecCtx, packet)
                if (ret == AVERROR_EAGAIN() || ret == AVERROR_EOF) break
                if (ret < 0) {
                    Logger.w(VideoEncoderContext::class) { "avcodec_receive_packet error: $ret" }
                    break
                }

                val data = ByteArray(packet.size())
                packet.data().get(data)

                val isKeyFrame = (packet.flags() and AV_PKT_FLAG_KEY) != 0

                // Check if this packet starts with SPS/PPS NAL units (codec config).
                // Annex-B start codes: 0x00 0x00 0x00 0x01 followed by NAL type.
                // SPS NAL type = 7, PPS NAL type = 8
                val isCodecConfig = data.size >= 5 &&
                        data[0] == 0x00.toByte() && data[1] == 0x00.toByte() &&
                        data[2] == 0x00.toByte() && data[3] == 0x01.toByte() &&
                        (data[4].toInt() and 0x1F) == 7

                emit(
                    EncodedStreamChunk(
                        data = data,
                        isKeyFrame = isKeyFrame,
                        isCodecConfig = isCodecConfig,
                        isAudio = false,
                    )
                )

                av_packet_unref(packet)
            }
        }

        private fun fillSourceFrame(image: BufferedImage) {
            val raster = image.raster
            val dataBuffer = raster.dataBuffer

            when (image.type) {
                BufferedImage.TYPE_3BYTE_BGR -> {
                    val pixels = (dataBuffer as DataBufferByte).data
                    srcFrame.data(0).put(pixels, 0, pixels.size)
                }

                BufferedImage.TYPE_INT_RGB, BufferedImage.TYPE_INT_ARGB, BufferedImage.TYPE_INT_BGR -> {
                    // Convert int-packed pixels to BGR24 for sws_scale.
                    // INT_RGB stores 0x00RRGGBB, so: bits 0-7=B, 8-15=G, 16-23=R.
                    val intPixels = (dataBuffer as DataBufferInt).data
                    val bgr = ByteArray(width * height * 3)
                    for (i in intPixels.indices) {
                        val px = intPixels[i]
                        val offset = i * 3
                        bgr[offset] = (px and 0xFF).toByte()              // B
                        bgr[offset + 1] = ((px shr 8) and 0xFF).toByte()  // G
                        bgr[offset + 2] = ((px shr 16) and 0xFF).toByte() // R
                    }
                    srcFrame.data(0).put(bgr, 0, bgr.size)
                }

                else -> {
                    // Fallback: convert to TYPE_3BYTE_BGR
                    val converted = BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR)
                    converted.graphics.drawImage(image, 0, 0, null)
                    val pixels = (converted.raster.dataBuffer as DataBufferByte).data
                    srcFrame.data(0).put(pixels, 0, pixels.size)
                }
            }
        }

        private fun convertToYuv() {
            sws_scale(
                swsCtx,
                srcFrame.data(),
                srcFrame.linesize(),
                0,
                height,
                yuvFrame.data(),
                yuvFrame.linesize(),
            )
        }

        fun release() {
            avcodec_free_context(codecCtx)
            sws_freeContext(swsCtx)
            av_frame_free(srcFrame)
            av_frame_free(yuvFrame)
            av_packet_free(packet)
        }

        companion object {
            fun create(width: Int, height: Int): VideoEncoderContext {
                val codec = avcodec_find_encoder(AV_CODEC_ID_H264)
                    ?: error("H.264 encoder not found. Ensure FFmpeg was built with libx264.")

                val codecCtx = avcodec_alloc_context3(codec).apply {
                    width(width)
                    height(height)
                    pix_fmt(AV_PIX_FMT_YUV420P)
                    time_base(av_make_q(1, VIDEO_FRAME_RATE))
                    framerate(av_make_q(VIDEO_FRAME_RATE, 1))
                    bit_rate(VIDEO_BIT_RATE.toLong())
                    gop_size(VIDEO_GOP_SIZE)
                    max_b_frames(0)
                    // Ensure Annex-B output (inline SPS/PPS, no global header)
                    flags(flags() or AV_CODEC_FLAG_GLOBAL_HEADER.inv())
                }

                val opts = AVDictionary()
                av_dict_set(opts, "preset", "ultrafast", 0)
                av_dict_set(opts, "tune", "zerolatency", 0)

                val ret = avcodec_open2(codecCtx, codec, opts)
                av_dict_free(opts)
                check(ret >= 0) { "Could not open H.264 codec: $ret" }

                val srcFrame = av_frame_alloc().apply {
                    format(AV_PIX_FMT_BGR24)
                    width(width)
                    height(height)
                }
                av_frame_get_buffer(srcFrame, 0)

                val yuvFrame = av_frame_alloc().apply {
                    format(AV_PIX_FMT_YUV420P)
                    width(width)
                    height(height)
                }
                av_frame_get_buffer(yuvFrame, 0)

                val swsCtx = sws_getContext(
                    width, height, AV_PIX_FMT_BGR24,
                    width, height, AV_PIX_FMT_YUV420P,
                    SWS_BILINEAR, null, null, DoublePointer(),
                ) ?: error("Could not initialise sws_getContext")

                val packet = av_packet_alloc()

                return VideoEncoderContext(width, height, codecCtx, swsCtx, srcFrame, yuvFrame, packet)
            }
        }
    }

    // ── Audio encoder context ──────────────────────────────────────

    /**
     * Encapsulates FFmpeg resources for AAC audio encoding.
     * Input: 16-bit signed LE mono PCM at 44100 Hz (matching [AudioRepository]).
     */
    private class AudioEncoderContext private constructor(
        private val codecCtx: AVCodecContext,
        private val frame: AVFrame,
        private val packet: AVPacket,
        private val frameSize: Int,       // samples per frame expected by the codec
    ) {
        private var pts = 0L
        private var residualBuf = ByteArray(0) // leftover PCM from previous encode() call

        fun encode(pcmData: ByteArray, emit: (EncodedStreamChunk) -> Unit) {
            // Accumulate PCM bytes (16-bit LE mono)
            val combined = residualBuf + pcmData
            val bytesPerFrame = frameSize * AUDIO_BYTES_PER_SAMPLE
            var offset = 0

            while (offset + bytesPerFrame <= combined.size) {
                // Convert 16-bit signed LE PCM → 32-bit float for FLTP format
                val sampleBuf = frame.data(0)
                sampleBuf.position(0L)
                val shortBuf = ByteBuffer.wrap(combined, offset, bytesPerFrame)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .asShortBuffer()
                val floatBytes = ByteBuffer.allocate(frameSize * 4).order(ByteOrder.nativeOrder())
                for (i in 0 until frameSize) {
                    floatBytes.putFloat(shortBuf.get(i).toFloat() / Short.MAX_VALUE)
                }
                sampleBuf.put(floatBytes.array(), 0, frameSize * 4)
                offset += bytesPerFrame

                frame.pts(pts)
                pts += frameSize

                var ret = avcodec_send_frame(codecCtx, frame)
                if (ret < 0 && ret != AVERROR_EAGAIN()) {
                    Logger.w(AudioEncoderContext::class) { "audio avcodec_send_frame error: $ret" }
                    continue
                }

                while (true) {
                    ret = avcodec_receive_packet(codecCtx, packet)
                    if (ret == AVERROR_EAGAIN() || ret == AVERROR_EOF) break
                    if (ret < 0) {
                        Logger.w(AudioEncoderContext::class) { "audio avcodec_receive_packet error: $ret" }
                        break
                    }

                    val data = ByteArray(packet.size())
                    packet.data().get(data)

                    emit(
                        EncodedStreamChunk(
                            data = data,
                            isKeyFrame = true, // every AAC frame is independently decodable
                            isCodecConfig = false,
                            isAudio = true,
                        )
                    )

                    av_packet_unref(packet)
                }
            }

            // Store remaining bytes for next call
            residualBuf = if (offset < combined.size) {
                combined.copyOfRange(offset, combined.size)
            } else {
                ByteArray(0)
            }
        }

        fun release() {
            avcodec_free_context(codecCtx)
            av_frame_free(frame)
            av_packet_free(packet)
        }

        companion object {
            fun create(): AudioEncoderContext {
                val codec = avcodec_find_encoder(AV_CODEC_ID_AAC)
                    ?: error("AAC encoder not found.")

                val codecCtx = avcodec_alloc_context3(codec).apply {
                    sample_fmt(AV_SAMPLE_FMT_FLTP) // FFmpeg's native AAC encoder requires FLTP
                    sample_rate(AUDIO_SAMPLE_RATE)
                    ch_layout().nb_channels(AUDIO_CHANNELS)
                    bit_rate(AUDIO_BIT_RATE.toLong())
                }

                val ret = avcodec_open2(codecCtx, codec, null as AVDictionary?)
                check(ret >= 0) { "Could not open AAC codec: $ret" }

                val frameSize = codecCtx.frame_size()

                val frame = av_frame_alloc().apply {
                    format(AV_SAMPLE_FMT_FLTP)
                    sample_rate(AUDIO_SAMPLE_RATE)
                    ch_layout().nb_channels(AUDIO_CHANNELS)
                    nb_samples(frameSize)
                }
                av_frame_get_buffer(frame, 0)

                val packet = av_packet_alloc()

                return AudioEncoderContext(codecCtx, frame, packet, frameSize)
            }
        }
    }

    private companion object {
        const val VIDEO_BIT_RATE = 2_000_000
        const val VIDEO_FRAME_RATE = 30
        const val VIDEO_GOP_SIZE = 60

        const val AUDIO_SAMPLE_RATE = 44100
        const val AUDIO_CHANNELS = 1
        const val AUDIO_BIT_RATE = 128_000
        /** 16-bit mono = 2 bytes per sample. */
        const val AUDIO_BYTES_PER_SAMPLE = 2
    }
}
