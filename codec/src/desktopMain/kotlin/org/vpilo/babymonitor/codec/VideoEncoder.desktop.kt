package org.vpilo.babymonitor.codec

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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
import org.bytedeco.ffmpeg.global.avcodec.avcodec_find_encoder_by_name
import org.bytedeco.ffmpeg.global.avcodec.avcodec_free_context
import org.bytedeco.ffmpeg.global.avcodec.avcodec_open2
import org.bytedeco.ffmpeg.global.avcodec.avcodec_receive_packet
import org.bytedeco.ffmpeg.global.avcodec.avcodec_send_frame
import org.bytedeco.ffmpeg.global.avutil.AVERROR_EAGAIN
import org.bytedeco.ffmpeg.global.avutil.AVERROR_EOF
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_BGRA
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
import org.vpilo.babymonitor.model.DesktopVideoStream
import org.vpilo.babymonitor.model.EncodedVideoStreamChunk
import org.vpilo.babymonitor.model.MediaFormats
import org.vpilo.babymonitor.model.MutableStreamingVideoFlow
import org.vpilo.babymonitor.model.OpaqueVideoStream
import kotlin.coroutines.CoroutineContext

@Suppress("LongMethod", "LoopWithTooManyJumpStatements")
actual class VideoEncoder actual constructor(
    source: OpaqueVideoStream,
    private val output: MutableStreamingVideoFlow,
    coroutineContext: CoroutineContext,
) {
    private val coroutineScope = CoroutineScope(coroutineContext)

    private var encodingJob: Job? = null

    private val videoStream: DesktopVideoStream =
        checkNotNull(source as? DesktopVideoStream) { "Invalid VideoStream" }

    init {
        videoStream.frameSize
            .onEach {
                if (isActive()) {
                    Logger.i(TAG) { "Resolution changed to $it" }
                    stop()
                    start()
                }
            }.launchIn(coroutineScope)
    }

    actual fun start() {
        if (isActive()) {
            Logger.w(TAG) { "Video encoder is already running, ignoring start request." }
            return
        }

        encodingJob =
            coroutineScope.launch {
                val frameSize = videoStream.frameSize.value
                var encoderContext: VideoEncoderContext? = null
                try {
                    videoStream.surface.collect { bitmap ->
                        if (!isActive) return@collect

                        if (encoderContext == null) {
                            VideoEncoderContext.create(frameSize.width, frameSize.height).also {
                                encoderContext = it
                                Logger.d(TAG) { "Video encoder configured for $frameSize" }
                            }
                        }

                        encoderContext?.encode(bitmap, videoStream.rotation.value) { chunk ->
                            output.tryEmit(chunk)
                        }
                    }
                } finally {
                    encoderContext?.release()
                }
            }
    }

    actual fun stop() {
        encodingJob?.cancel()
        encodingJob = null
    }

    private fun isActive() = encodingJob?.isActive == true

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

        fun encode(
            bitmap: ImageBitmap,
            rotation: Int,
            emit: (EncodedVideoStreamChunk) -> Unit,
        ) {
            fillSourceFrameFromBitmap(bitmap)
            convertToYuv()

            yuvFrame.pts(pts++)

            var ret = avcodec_send_frame(codecCtx, yuvFrame)
            if (ret < 0 && ret != AVERROR_EAGAIN()) {
                Logger.w(TAG) { "avcodec_send_frame error: $ret" }
                return
            }

            while (true) {
                ret = avcodec_receive_packet(codecCtx, packet)
                if (ret == AVERROR_EAGAIN() || ret == AVERROR_EOF) break
                if (ret < 0) {
                    Logger.w(TAG) { "avcodec_receive_packet error: $ret" }
                    break
                }

                val data = ByteArray(packet.size())
                packet.data().get(data)

                val isKeyFrame = (packet.flags() and AV_PKT_FLAG_KEY) != 0
                emit(
                    EncodedVideoStreamChunk(
                        data = data,
                        isKeyFrame = isKeyFrame,
                        rotation = rotation,
                        frameWidth = width,
                        frameHeight = height,
                    ),
                )

                av_packet_unref(packet)
            }
        }

        private fun fillSourceFrameFromBitmap(bitmap: ImageBitmap) {
            // Skia bitmaps store pixels in BGRA8888 by default on Desktop.
            val pixels =
                bitmap.asSkiaBitmap().readPixels()
                    ?: error("Failed to read pixels from ImageBitmap")
            srcFrame.data(0).put(pixels, 0, pixels.size)
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
            fun create(
                width: Int,
                height: Int,
            ): VideoEncoderContext {
                // Prefer libx264 if available, falling back to libopenh264 if unavailable.
                val codec =
                    avcodec_find_encoder_by_name("libx264")
                        ?: avcodec_find_encoder_by_name("libopenh264")
                        ?: avcodec_find_encoder(AV_CODEC_ID_H264)
                        ?: error("H.264 encoder not found.")

                val encoderName = codec.name().getString()

                val codecCtx =
                    avcodec_alloc_context3(codec).apply {
                        width(width)
                        height(height)
                        pix_fmt(AV_PIX_FMT_YUV420P)
                        time_base(av_make_q(1, MediaFormats.Video.FRAME_RATE))
                        framerate(av_make_q(MediaFormats.Video.FRAME_RATE, 1))
                        bit_rate(MediaFormats.Video.BIT_RATE.toLong())
                        gop_size(MediaFormats.Video.FRAME_RATE * MediaFormats.Video.KEY_FRAME_INTERVAL_SECONDS)
                        max_b_frames(0)
                        // Ensure Annex-B output: clear the GLOBAL_HEADER flag so SPS/PPS are emitted inline with the bitstream.
                        flags(flags() and AV_CODEC_FLAG_GLOBAL_HEADER.inv())
                    }

                // libopenh264 doesn't support presets/tunes;
                // use its own low-latency knobs instead.
                val opts = AVDictionary()
                when (encoderName) {
                    "libx264" -> {
                        av_dict_set(opts, "preset", "ultrafast", 0)
                        av_dict_set(opts, "tune", "zerolatency", 0)
                    }

                    "libopenh264" -> {
                        av_dict_set(opts, "rc_mode", "bitrate", 0)
                    }
                }

                val ret = avcodec_open2(codecCtx, codec, opts)
                av_dict_free(opts)
                check(ret >= 0) { "Could not open H.264 codec ($encoderName): $ret" }

                val srcFrame =
                    av_frame_alloc().apply {
                        format(AV_PIX_FMT_BGRA)
                        width(width)
                        height(height)
                    }
                av_frame_get_buffer(srcFrame, 0)

                val yuvFrame =
                    av_frame_alloc().apply {
                        format(AV_PIX_FMT_YUV420P)
                        width(width)
                        height(height)
                    }
                av_frame_get_buffer(yuvFrame, 0)

                val swsCtx =
                    sws_getContext(
                        width,
                        height,
                        AV_PIX_FMT_BGRA,
                        width,
                        height,
                        AV_PIX_FMT_YUV420P,
                        SWS_BILINEAR,
                        null,
                        null,
                        DoublePointer(),
                    ) ?: error("Could not initialise sws_getContext")

                val packet = av_packet_alloc()

                return VideoEncoderContext(width, height, codecCtx, swsCtx, srcFrame, yuvFrame, packet)
            }
        }
    }

    private companion object {
        private val TAG = VideoEncoder::class
    }
}
