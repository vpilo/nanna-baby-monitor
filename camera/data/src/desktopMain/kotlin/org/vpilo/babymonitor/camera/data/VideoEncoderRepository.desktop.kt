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
import org.vpilo.babymonitor.model.CameraFrame
import org.vpilo.babymonitor.model.CameraFrameRepository
import org.vpilo.babymonitor.model.Configuration
import org.vpilo.babymonitor.model.EncodedStreamChunk
import org.vpilo.babymonitor.model.VideoFeedFlow
import org.vpilo.babymonitor.model.VideoFeedRepository
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import java.awt.image.DataBufferInt
import kotlin.reflect.KClass

actual class VideoEncoderRepository(
    private val cameraFrameRepository: CameraFrameRepository,
) : VideoFeedRepository,
    SharedResourceRepository<EncodedStreamChunk>(
        bufferCapacity = Configuration.MAX_VIDEO_STREAM_BUFFER_SIZE,
    ) {

    override val chunks: VideoFeedFlow = collector.asSharedFlow()

    private var encodeJob: Job? = null

    override fun start() {
        if (encodeJob?.isActive == true) {
            Logger.w(TAG) { "Encoder is already running, ignoring start request." }
            return
        }

        encodeJob = coroutineScope.launch {
            var encoderCtx: EncoderContext? = null
            try {
                cameraFrameRepository.frames.collect { frame ->
                    if (!isActive) return@collect

                    val w = frame.image.width
                    val h = frame.image.height

                    // (Re)create encoder if resolution changed
                    if (encoderCtx == null || encoderCtx!!.width != w || encoderCtx!!.height != h) {
                        encoderCtx?.release()
                        encoderCtx = EncoderContext.create(w, h)
                        Logger.d(TAG) { "Encoder configured for ${w}x${h}" }
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

    override fun stop() {
        encodeJob?.cancel()
        encodeJob = null
    }

    override val TAG: KClass<*> = VideoEncoderRepository::class

    /**
     * Encapsulates all FFmpeg resources for a given resolution.
     */
    private class EncoderContext private constructor(
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
                Logger.w(EncoderContext::class) { "avcodec_send_frame error: $ret" }
                return
            }

            // Receive all available packets
            while (true) {
                ret = avcodec_receive_packet(codecCtx, packet)
                if (ret == AVERROR_EAGAIN() || ret == AVERROR_EOF) break
                if (ret < 0) {
                    Logger.w(EncoderContext::class) { "avcodec_receive_packet error: $ret" }
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
                        timestampUs = packet.pts() * 1_000_000L / FRAME_RATE,
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
                    // Convert int-packed pixels to BGR24 for sws_scale
                    val intPixels = (dataBuffer as DataBufferInt).data
                    val bgr = ByteArray(width * height * 3)
                    for (i in intPixels.indices) {
                        val px = intPixels[i]
                        val offset = i * 3
                        bgr[offset] = (px and 0xFF).toByte()          // B
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
            fun create(width: Int, height: Int): EncoderContext {
                // Find H.264 encoder
                val codec = avcodec_find_encoder(AV_CODEC_ID_H264)
                    ?: error("H.264 encoder not found. Ensure FFmpeg was built with libx264.")

                val codecCtx = avcodec_alloc_context3(codec).apply {
                    width(width)
                    height(height)
                    pix_fmt(AV_PIX_FMT_YUV420P)
                    time_base(av_make_q(1, FRAME_RATE))
                    framerate(av_make_q(FRAME_RATE, 1))
                    bit_rate(BIT_RATE.toLong())
                    gop_size(GOP_SIZE)
                    max_b_frames(0) // No B-frames for low latency
                    // Set Annex-B output (this is the default for libx264, but be explicit)
                    flags(flags() or AV_CODEC_FLAG_GLOBAL_HEADER.inv()) // no global header → inline SPS/PPS
                }

                // Low-latency x264 options
                val opts = AVDictionary()
                av_dict_set(opts, "preset", "ultrafast", 0)
                av_dict_set(opts, "tune", "zerolatency", 0)

                val ret = avcodec_open2(codecCtx, codec, opts)
                av_dict_free(opts)
                check(ret >= 0) { "Could not open codec: $ret" }

                // Allocate source frame (BGR24 from BufferedImage)
                val srcFrame = av_frame_alloc().apply {
                    format(AV_PIX_FMT_BGR24)
                    width(width)
                    height(height)
                }
                av_frame_get_buffer(srcFrame, 0)

                // Allocate YUV420P frame
                val yuvFrame = av_frame_alloc().apply {
                    format(AV_PIX_FMT_YUV420P)
                    width(width)
                    height(height)
                }
                av_frame_get_buffer(yuvFrame, 0)

                // Color-space converter: BGR24 → YUV420P
                val swsCtx = sws_getContext(
                    width, height, AV_PIX_FMT_BGR24,
                    width, height, AV_PIX_FMT_YUV420P,
                    SWS_BILINEAR, null, null, DoublePointer(),
                ) ?: error("Could not initialise sws_getContext")

                val packet = av_packet_alloc()

                return EncoderContext(width, height, codecCtx, swsCtx, srcFrame, yuvFrame, packet)
            }
        }
    }

    private companion object {
        const val BIT_RATE = 2_000_000 // 2 Mbps
        const val FRAME_RATE = 30
        const val GOP_SIZE = 60 // keyframe every 2 seconds at 30 fps
    }
}
