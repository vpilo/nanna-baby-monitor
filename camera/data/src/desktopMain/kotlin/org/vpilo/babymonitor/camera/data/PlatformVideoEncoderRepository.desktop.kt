package org.vpilo.babymonitor.camera.data

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.bytedeco.ffmpeg.avcodec.AVCodecContext
import org.bytedeco.ffmpeg.avcodec.AVPacket
import org.bytedeco.ffmpeg.avutil.AVDictionary
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_FLAG_GLOBAL_HEADER
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264
import org.bytedeco.ffmpeg.global.avcodec.AV_PKT_FLAG_KEY
import org.bytedeco.ffmpeg.global.avcodec.av_packet_alloc
import org.bytedeco.ffmpeg.global.avcodec.av_packet_free
import org.bytedeco.ffmpeg.global.avcodec.av_packet_unref
import org.bytedeco.ffmpeg.global.avcodec.avcodec_alloc_context3
import org.bytedeco.ffmpeg.global.avcodec.avcodec_find_encoder
import org.bytedeco.ffmpeg.global.avcodec.avcodec_free_context
import org.bytedeco.ffmpeg.global.avcodec.avcodec_open2
import org.bytedeco.ffmpeg.global.avcodec.avcodec_receive_packet
import org.bytedeco.ffmpeg.global.avcodec.avcodec_send_frame
import org.bytedeco.ffmpeg.global.avutil.AVERROR_EAGAIN
import org.bytedeco.ffmpeg.global.avutil.AVERROR_EOF
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_BGR24
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P
import org.bytedeco.ffmpeg.global.avutil.av_dict_free
import org.bytedeco.ffmpeg.global.avutil.av_dict_set
import org.bytedeco.ffmpeg.global.avutil.av_frame_alloc
import org.bytedeco.ffmpeg.global.avutil.av_frame_free
import org.bytedeco.ffmpeg.global.avutil.av_frame_get_buffer
import org.bytedeco.ffmpeg.global.avutil.av_make_q
import org.bytedeco.ffmpeg.global.swscale.SWS_BILINEAR
import org.bytedeco.ffmpeg.global.swscale.sws_freeContext
import org.bytedeco.ffmpeg.global.swscale.sws_getContext
import org.bytedeco.ffmpeg.global.swscale.sws_scale
import org.bytedeco.ffmpeg.swscale.SwsContext
import org.bytedeco.javacpp.DoublePointer
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.CameraFrame
import org.vpilo.babymonitor.model.MediaFormats
import org.vpilo.babymonitor.model.EncodedVideoStreamChunk
import org.vpilo.babymonitor.model.StreamingVideoFlow
import org.vpilo.babymonitor.model.StreamingVideoRepository
import org.vpilo.babymonitor.model.VideoCaptureRepository
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import java.awt.image.DataBufferInt

actual class PlatformVideoEncoderRepository(
    private val videoCaptureRepository: VideoCaptureRepository,
) : StreamingVideoRepository,
    SharedResourceRepository<EncodedVideoStreamChunk>(
        bufferCapacity = MediaFormats.BufferSizes.MAX_VIDEO_STREAM_BUFFER_SIZE,
    ) {

    override val chunks: StreamingVideoFlow = collector.asSharedFlow()

    private var videoEncodeJob: Job? = null

    override fun start() {
        if (videoEncodeJob?.isActive == true) {
            Logger.w(TAG) { "Video encoder is already running, ignoring start request." }
            return
        }

        videoEncodeJob = coroutineScope.launch {
            var encoderCtx: VideoEncoderContext? = null
            try {
                videoCaptureRepository.frames.collect { frame ->
                    if (!isActive) return@collect

                    val w = frame.image.width
                    val h = frame.image.height

                    // (Re)create encoder if resolution changed
                    if (encoderCtx == null || encoderCtx!!.width != w || encoderCtx!!.height != h) {
                        encoderCtx?.release()
                        encoderCtx = VideoEncoderContext.create(w, h)
                        Logger.d(TAG) { "Video encoder configured for ${w}x${h}" }
                    }

                    encoderCtx.encode(frame) { chunk ->
                        collector.tryEmit(chunk)
                    }
                }
            } finally {
                encoderCtx?.release()
            }
        }
    }

    override fun stop() {
        videoEncodeJob?.cancel()
        videoEncodeJob = null
    }

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

        fun encode(frame: CameraFrame, emit: (EncodedVideoStreamChunk) -> Unit) {
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
                    EncodedVideoStreamChunk(
                        data = data,
                        isKeyFrame = isKeyFrame,
                        isCodecConfig = isCodecConfig,
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
                    time_base(av_make_q(1, MediaFormats.Video.FRAME_RATE))
                    framerate(av_make_q(MediaFormats.Video.FRAME_RATE, 1))
                    bit_rate(MediaFormats.Video.BIT_RATE.toLong())
                    gop_size(MediaFormats.Video.FRAME_RATE * MediaFormats.Video.KEY_FRAME_INTERVAL_SECONDS)
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

    override val TAG = PlatformVideoEncoderRepository::class
}
