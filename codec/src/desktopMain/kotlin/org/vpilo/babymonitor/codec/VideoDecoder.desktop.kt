package org.vpilo.babymonitor.codec

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.bytedeco.ffmpeg.avcodec.AVCodecContext
import org.bytedeco.ffmpeg.avcodec.AVPacket
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264
import org.bytedeco.ffmpeg.global.avcodec.av_packet_alloc
import org.bytedeco.ffmpeg.global.avcodec.av_packet_free
import org.bytedeco.ffmpeg.global.avcodec.avcodec_alloc_context3
import org.bytedeco.ffmpeg.global.avcodec.avcodec_find_decoder
import org.bytedeco.ffmpeg.global.avcodec.avcodec_free_context
import org.bytedeco.ffmpeg.global.avcodec.avcodec_open2
import org.bytedeco.ffmpeg.global.avcodec.avcodec_receive_frame
import org.bytedeco.ffmpeg.global.avcodec.avcodec_send_packet
import org.bytedeco.ffmpeg.global.avutil.AVERROR_EAGAIN
import org.bytedeco.ffmpeg.global.avutil.AVERROR_EOF
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_BGR24
import org.bytedeco.ffmpeg.global.avutil.av_frame_alloc
import org.bytedeco.ffmpeg.global.avutil.av_frame_free
import org.bytedeco.ffmpeg.global.avutil.av_frame_get_buffer
import org.bytedeco.ffmpeg.global.swscale.SWS_BILINEAR
import org.bytedeco.ffmpeg.global.swscale.sws_freeContext
import org.bytedeco.ffmpeg.global.swscale.sws_getContext
import org.bytedeco.ffmpeg.global.swscale.sws_scale
import org.bytedeco.ffmpeg.swscale.SwsContext
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.DoublePointer
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.DesktopVideoStream
import org.vpilo.babymonitor.model.OpaqueVideoStream
import org.vpilo.babymonitor.model.StreamingVideoFlow
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import kotlin.coroutines.CoroutineContext

@Suppress("LongMethod", "LoopWithTooManyJumpStatements")
actual class VideoDecoder actual constructor(
    private val input: StreamingVideoFlow,
    coroutineContext: CoroutineContext,
) {
    private val coroutineScope = CoroutineScope(coroutineContext)

    private var decodeJob: Job? = null

    private val mutableVideoStream = DesktopVideoStream()
    actual val videoStream: OpaqueVideoStream = mutableVideoStream

    actual fun start() {
        if (decodeJob?.isActive == true) {
            Logger.w(TAG) { "Video decoder is already running, ignoring start request." }
            return
        }

        decodeJob =
            coroutineScope.launch {
                var lastRotation = 0
                var lastFrameWidth = 0
                var lastFrameHeight = 0
                var ctx: VideoDecoderContext? = null
                try {
                    input.collect { chunk ->
                        if (!isActive) return@collect

                        if (chunk.frameWidth != lastFrameWidth || chunk.frameHeight != lastFrameHeight) {
                            Logger.d(TAG) { "Frame size updated: ${chunk.frameWidth}x${chunk.frameHeight}" }
                            mutableVideoStream.setFrameSize(chunk.frameWidth, chunk.frameHeight)
                            lastFrameWidth = chunk.frameWidth
                            lastFrameHeight = chunk.frameHeight
                        }

                        if (chunk.rotation != lastRotation) {
                            Logger.d(TAG) { "Rotation updated: ${chunk.rotation}" }
                            mutableVideoStream.setRotation(chunk.rotation)
                            lastRotation = chunk.rotation
                        }

                        if (ctx == null) {
                            ctx = VideoDecoderContext.create()
                            Logger.d(TAG) { "Video decoder started" }
                        }

                        ctx.decode(chunk.data) { bitmap ->
                            mutableVideoStream.onFrame(bitmap)
                        }
                    }
                } finally {
                    ctx?.release()
                }
            }
    }

    actual fun stop() {
        decodeJob?.cancel()
        decodeJob = null
    }

    /**
     * Encapsulates FFmpeg resources for H.264 video decoding.
     * Decodes Annex-B NAL units → YUV420P → BGR24 → BufferedImage → ImageBitmap.
     *
     * YUV→RGB conversion via sws_scale is required because BufferedImage / Compose ImageBitmap
     * only support RGB-family pixel formats. The conversion is SIMD-accelerated in FFmpeg
     * and takes <1 ms for typical resolutions.
     */
    private class VideoDecoderContext private constructor(
        private val codecCtx: AVCodecContext,
        private val decodedFrame: AVFrame,
        private val packet: AVPacket,
    ) {
        private var swsCtx: SwsContext? = null
        private var bgrFrame: AVFrame? = null
        private var lastWidth = 0
        private var lastHeight = 0

        fun decode(
            nalData: ByteArray,
            emit: (ImageBitmap) -> Unit,
        ) {
            // Fill packet with the raw NAL unit data
            val dataPtr = BytePointer(nalData.size.toLong())
            dataPtr.put(nalData, 0, nalData.size)
            dataPtr.position(0L)

            packet.data(dataPtr)
            packet.size(nalData.size)

            var ret = avcodec_send_packet(codecCtx, packet)
            // Detach the packet from the data pointer before any early return,
            // so FFmpeg does not attempt to free our manually-managed buffer.
            packet.data(null as BytePointer?)
            packet.size(0)

            if (ret < 0 && ret != AVERROR_EAGAIN()) {
                dataPtr.close()
                Logger.w(TAG) { "avcodec_send_packet error: $ret" }
                return
            }

            // Receive all decoded frames from this packet
            while (true) {
                ret = avcodec_receive_frame(codecCtx, decodedFrame)
                if (ret == AVERROR_EAGAIN() || ret == AVERROR_EOF) break
                if (ret < 0) {
                    Logger.w(TAG) { "avcodec_receive_frame error: $ret" }
                    break
                }

                val w = decodedFrame.width()
                val h = decodedFrame.height()

                // (Re)create sws context if resolution changed
                if (w != lastWidth || h != lastHeight) {
                    swsCtx?.let { sws_freeContext(it) }
                    bgrFrame?.let { av_frame_free(it) }

                    bgrFrame =
                        av_frame_alloc().apply {
                            format(AV_PIX_FMT_BGR24)
                            width(w)
                            height(h)
                        }
                    av_frame_get_buffer(bgrFrame, 0)

                    swsCtx = sws_getContext(
                        w,
                        h,
                        decodedFrame.format(),
                        w,
                        h,
                        AV_PIX_FMT_BGR24,
                        SWS_BILINEAR,
                        null,
                        null,
                        DoublePointer(),
                    ) ?: error("Could not initialise sws_getContext for decoding")

                    lastWidth = w
                    lastHeight = h
                    Logger.d(TAG) { "Decoder sws context configured for ${w}x$h" }
                }

                // Convert YUV → BGR
                sws_scale(
                    swsCtx,
                    decodedFrame.data(),
                    decodedFrame.linesize(),
                    0,
                    h,
                    bgrFrame!!.data(),
                    bgrFrame!!.linesize(),
                )

                // Copy BGR bytes to a BufferedImage
                val image = BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR)
                val destPixels = (image.raster.dataBuffer as DataBufferByte).data
                bgrFrame!!.data(0).get(destPixels)

                emit(image.toComposeImageBitmap())
            }

            dataPtr.close()
        }

        fun release() {
            swsCtx?.let { sws_freeContext(it) }
            bgrFrame?.let { av_frame_free(it) }
            avcodec_free_context(codecCtx)
            av_frame_free(decodedFrame)
            av_packet_free(packet)
        }

        companion object {
            private const val TAG = "VideoDecoderContext"

            fun create(): VideoDecoderContext {
                val codec =
                    avcodec_find_decoder(AV_CODEC_ID_H264)
                        ?: error("H.264 decoder not found.")

                val codecCtx = avcodec_alloc_context3(codec)
                // Enable low-delay decoding
                codecCtx.flags2(codecCtx.flags2() or 1) // AV_CODEC_FLAG2_FAST

                val ret = avcodec_open2(codecCtx, codec, null as org.bytedeco.ffmpeg.avutil.AVDictionary?)
                check(ret >= 0) { "Could not open H.264 decoder: $ret" }

                val decodedFrame = av_frame_alloc()
                val packet = av_packet_alloc()

                return VideoDecoderContext(codecCtx, decodedFrame, packet)
            }
        }
    }

    private companion object {
        private val TAG = VideoDecoder::class
    }
}
