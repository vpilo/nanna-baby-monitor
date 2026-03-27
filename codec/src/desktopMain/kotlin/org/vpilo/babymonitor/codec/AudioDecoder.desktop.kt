package org.vpilo.babymonitor.codec

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.bytedeco.ffmpeg.avcodec.AVCodecContext
import org.bytedeco.ffmpeg.avcodec.AVPacket
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_OPUS
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
import org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_S16
import org.bytedeco.ffmpeg.global.avutil.av_channel_layout_default
import org.bytedeco.ffmpeg.global.avutil.av_frame_alloc
import org.bytedeco.ffmpeg.global.avutil.av_frame_free
import org.bytedeco.ffmpeg.global.swresample.swr_alloc_set_opts2
import org.bytedeco.ffmpeg.global.swresample.swr_convert
import org.bytedeco.ffmpeg.global.swresample.swr_free
import org.bytedeco.ffmpeg.global.swresample.swr_init
import org.bytedeco.ffmpeg.swresample.SwrContext
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.PointerPointer
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.MediaFormats
import org.vpilo.babymonitor.model.MutableAudioFrameFlow
import org.vpilo.babymonitor.model.StreamingAudioFlow
import kotlin.coroutines.CoroutineContext

actual class AudioDecoder actual constructor(
    private val input: StreamingAudioFlow,
    private val output: MutableAudioFrameFlow,
    coroutineContext: CoroutineContext,
) {
    private val coroutineScope = CoroutineScope(coroutineContext)
    private var decodeJob: Job? = null

    actual fun start() {
        if (decodeJob?.isActive == true) {
            Logger.w(TAG) { "Audio decoder is already running, ignoring start request." }
            return
        }

        decodeJob =
            coroutineScope.launch {
                val ctx = AudioDecoderContext.create(output)
                Logger.d(TAG) { "Audio decoder started" }

                try {
                    input.collect { chunk ->
                        ctx.decode(chunk.data)
                    }
                } finally {
                    ctx.release()
                }
            }
    }

    actual fun stop() {
        decodeJob?.cancel()
        decodeJob = null
    }

    /**
     * Encapsulates FFmpeg resources for Opus audio decoding and playback via SourceDataLine.
     * Opus decoders in FFmpeg output AV_SAMPLE_FMT_FLTP (32-bit float planar).
     * We use SwrContext to resample to S16 interleaved for SourceDataLine.
     */
    private class AudioDecoderContext private constructor(
        private val codecCtx: AVCodecContext,
        private val decodedFrame: AVFrame,
        private val packet: AVPacket,
        private val output: MutableAudioFrameFlow,
        private val swrCtx: SwrContext,
    ) {
        fun decode(opusData: ByteArray) {
            val dataPtr = BytePointer(opusData.size.toLong())
            dataPtr.put(opusData, 0, opusData.size)
            dataPtr.position(0L)

            packet.data(dataPtr)
            packet.size(opusData.size)

            var ret = avcodec_send_packet(codecCtx, packet)
            // Detach the packet from the data pointer before any early return,
            // so FFmpeg does not attempt to free our manually-managed buffer.
            packet.data(null as BytePointer?)
            packet.size(0)

            if (ret < 0 && ret != AVERROR_EAGAIN()) {
                dataPtr.close()
                Logger.w(TAG) { "audio avcodec_send_packet error: $ret" }
                return
            }

            while (true) {
                ret = avcodec_receive_frame(codecCtx, decodedFrame)
                if (ret == AVERROR_EAGAIN() || ret == AVERROR_EOF) break
                if (ret < 0) {
                    Logger.w(TAG) { "audio avcodec_receive_frame error: $ret" }
                    break
                }

                val nbSamples = decodedFrame.nb_samples()
                val channels = MediaFormats.Audio.CHANNELS
                val bytesPerSample = MediaFormats.Audio.SAMPLE_SIZE_BITS / 8
                val outBufSize = nbSamples * channels * bytesPerSample

                // Allocate output buffer for S16 interleaved
                val outPtr = BytePointer(outBufSize.toLong())
                val outPtrs = PointerPointer<BytePointer>(1L).put(0, outPtr)

                try {
                    val convertedSamples =
                        swr_convert(
                            swrCtx,
                            outPtrs,
                            nbSamples,
                            decodedFrame.data(),
                            nbSamples,
                        )

                    if (convertedSamples > 0) {
                        val pcmSize = convertedSamples * channels * bytesPerSample
                        val pcmBytes = ByteArray(pcmSize)
                        outPtr.position(0L).get(pcmBytes)
                        output.tryEmit(pcmBytes)
                    }
                } finally {
                    outPtrs.close()
                    outPtr.close()
                }
            }

            dataPtr.close()
        }

        fun release() {
            swr_free(swrCtx)
            avcodec_free_context(codecCtx)
            av_frame_free(decodedFrame)
            av_packet_free(packet)
        }

        companion object {
            private const val TAG = "AudioDecoderContext"

            fun create(output: MutableAudioFrameFlow): AudioDecoderContext {
                val codec =
                    avcodec_find_decoder(AV_CODEC_ID_OPUS)
                        ?: error("Opus decoder not found.")

                val codecCtx =
                    avcodec_alloc_context3(codec).apply {
                        sample_rate(MediaFormats.Audio.SAMPLE_RATE)
                        av_channel_layout_default(ch_layout(), MediaFormats.Audio.CHANNELS)
                    }

                val ret = avcodec_open2(codecCtx, codec, null as org.bytedeco.ffmpeg.avutil.AVDictionary?)
                check(ret >= 0) { "Could not open Opus decoder: $ret" }

                // Set up resampler: decoder output format (FLTP) → S16 interleaved
                val swrCtx = SwrContext()
                val outLayout =
                    org.bytedeco.ffmpeg.avutil
                        .AVChannelLayout()
                av_channel_layout_default(outLayout, MediaFormats.Audio.CHANNELS)
                val inLayout =
                    org.bytedeco.ffmpeg.avutil
                        .AVChannelLayout()
                av_channel_layout_default(inLayout, MediaFormats.Audio.CHANNELS)

                val swrRet =
                    swr_alloc_set_opts2(
                        swrCtx,
                        outLayout,
                        AV_SAMPLE_FMT_S16,
                        MediaFormats.Audio.SAMPLE_RATE,
                        inLayout,
                        codecCtx.sample_fmt(),
                        MediaFormats.Audio.SAMPLE_RATE,
                        0,
                        null,
                    )
                check(swrRet >= 0) { "Could not set swr options: $swrRet" }

                val initRet = swr_init(swrCtx)
                check(initRet >= 0) { "Could not init swr context: $initRet" }

                val decodedFrame = av_frame_alloc()
                val packet = av_packet_alloc()

                return AudioDecoderContext(codecCtx, decodedFrame, packet, output, swrCtx)
            }
        }
    }

    private companion object {
        private val TAG = AudioDecoder::class
    }
}
