package org.vpilo.babymonitor.codec

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.bytedeco.ffmpeg.avcodec.AVCodecContext
import org.bytedeco.ffmpeg.avcodec.AVPacket
import org.bytedeco.ffmpeg.avutil.AVDictionary
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_AAC
import org.bytedeco.ffmpeg.global.avcodec.av_packet_alloc
import org.bytedeco.ffmpeg.global.avcodec.av_packet_free
import org.bytedeco.ffmpeg.global.avcodec.av_packet_unref
import org.bytedeco.ffmpeg.global.avcodec.avcodec_alloc_context3
import org.bytedeco.ffmpeg.global.avcodec.avcodec_find_encoder
import org.bytedeco.ffmpeg.global.avcodec.avcodec_free_context
import org.bytedeco.ffmpeg.global.avcodec.avcodec_open2
import org.bytedeco.ffmpeg.global.avcodec.avcodec_receive_packet
import org.bytedeco.ffmpeg.global.avcodec.avcodec_send_frame
import org.bytedeco.ffmpeg.global.avutil.AVERROR_EOF
import org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_FLTP
import org.bytedeco.ffmpeg.global.avutil.av_channel_layout_default
import org.bytedeco.ffmpeg.global.avutil.av_frame_alloc
import org.bytedeco.ffmpeg.global.avutil.av_frame_free
import org.bytedeco.ffmpeg.global.avutil.av_frame_get_buffer
import org.bytedeco.ffmpeg.presets.avutil.AVERROR_EAGAIN
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.AudioCaptureRepository
import org.vpilo.babymonitor.model.MediaFormats
import org.vpilo.babymonitor.model.EncodedAudioStreamChunk
import org.vpilo.babymonitor.model.StreamingAudioFlow
import org.vpilo.babymonitor.model.repository.StreamingAudioRepository
import org.vpilo.babymonitor.model.repository.SharedResourceRepository
import java.nio.ByteBuffer
import java.nio.ByteOrder

actual class PlatformAudioEncoderRepository(
    private val audioCaptureRepository: AudioCaptureRepository,
) : StreamingAudioRepository,
    SharedResourceRepository<EncodedAudioStreamChunk>(
        bufferCapacity = MediaFormats.BufferSizes.MAX_VIDEO_STREAM_BUFFER_SIZE,
    ) {

    override val chunks: StreamingAudioFlow = collector.asSharedFlow()

    private var audioEncodeJob: Job? = null

    override fun start() {
        if (audioEncodeJob?.isActive == true) {
            Logger.w(TAG) { "Audio encoder is already running, ignoring start request." }
            return
        }

        audioEncodeJob = coroutineScope.launch {
            val audioCtx = AudioEncoderContext.create()
            Logger.d(TAG) { "Audio encoder started" }

            try {
                audioCaptureRepository.samples.collect { pcmChunk ->
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

    override fun stop() {
        audioEncodeJob?.cancel()
        audioEncodeJob = null
    }

    /**
     * Encapsulates FFmpeg resources for AAC audio encoding.
     * Input: 16-bit signed LE mono PCM at 44100 Hz.
     */
    private class AudioEncoderContext private constructor(
        private val codecCtx: AVCodecContext,
        private val frame: AVFrame,
        private val packet: AVPacket,
        private val frameSize: Int,       // samples per frame expected by the codec
    ) {
        private var pts = 0L
        private var residualBuf = ByteArray(0) // leftover PCM from previous encode() call

        fun encode(pcmData: ByteArray, emit: (EncodedAudioStreamChunk) -> Unit) {
            // Accumulate PCM bytes (16-bit LE mono)
            val combined = residualBuf + pcmData
            val bytesPerFrame = frameSize * (MediaFormats.Audio.SAMPLE_SIZE_BITS / 8)
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
                        EncodedAudioStreamChunk(
                            data = data,
                            isCodecConfig = false,
                        ),
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
                    sample_rate(MediaFormats.Audio.SAMPLE_RATE)
                    av_channel_layout_default(ch_layout(), MediaFormats.Audio.CHANNELS)
                    bit_rate(MediaFormats.Audio.BIT_RATE.toLong())
                }

                val ret = avcodec_open2(codecCtx, codec, null as AVDictionary?)
                check(ret >= 0) { "Could not open AAC codec: $ret" }

                val frameSize = codecCtx.frame_size()

                val frame = av_frame_alloc().apply {
                    format(AV_SAMPLE_FMT_FLTP)
                    sample_rate(MediaFormats.Audio.SAMPLE_RATE)
                    av_channel_layout_default(ch_layout(), MediaFormats.Audio.CHANNELS)
                    nb_samples(frameSize)
                }
                av_frame_get_buffer(frame, 0)

                val packet = av_packet_alloc()

                return AudioEncoderContext(codecCtx, frame, packet, frameSize)
            }
        }
    }

    override val TAG = PlatformAudioEncoderRepository::class
}
