package org.vpilo.babymonitor.codec

import android.media.MediaCodec
import android.media.MediaFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.MediaFormats
import org.vpilo.babymonitor.model.MutableAudioFrameFlow
import org.vpilo.babymonitor.model.StreamingAudioFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.CoroutineContext

@Suppress("LoopWithTooManyJumpStatements")
actual class AudioDecoder actual constructor(
    private val input: StreamingAudioFlow,
    private val output: MutableAudioFrameFlow,
    coroutineContext: CoroutineContext,
) {
    private val coroutineScope = CoroutineScope(coroutineContext)

    private var decodeJob: Job? = null
    private var decoder: MediaCodec? = null

    actual fun start() {
        if (decodeJob?.isActive == true) {
            Logger.w(TAG) { "Audio decoder is already running, ignoring start request." }
            return
        }

        lateinit var codec: MediaCodec
        decodeJob =
            coroutineScope
                .launch {
                    // Channel to receive available input buffer indices from the codec callback
                    val inputBufferAvailable = Channel<Int>(Channel.BUFFERED)
                    // Channel to receive output buffer indices + info from the codec callback
                    val outputBufferAvailable = Channel<OutputBufferInfo>(Channel.BUFFERED)

                    codec =
                        createAudioDecoder(
                            inputBufferAvailable = inputBufferAvailable,
                            outputBufferAvailable = outputBufferAvailable,
                            onCodecError = { error ->
                                Logger.e(TAG, error) { "Audio decoder error; stopping decode loop" }
                                inputBufferAvailable.close()
                                outputBufferAvailable.close()
                            },
                        )
                    decoder = codec
                    Logger.d(TAG) { "Audio decoder started" }

                    // Launch a coroutine to drain decoded output buffers
                    val drainJob =
                        launch {
                            for (out in outputBufferAvailable) {
                                try {
                                    val outputBuffer = codec.getOutputBuffer(out.index) ?: continue
                                    outputBuffer.position(out.offset)
                                    outputBuffer.limit(out.offset + out.size)

                                    val pcmBytes = ByteArray(out.size)
                                    outputBuffer.get(pcmBytes)

                                    output.tryEmit(pcmBytes)
                                } catch (ex: IllegalStateException) {
                                    Logger.w(TAG, ex) { "Failed to drain audio output buffer" }
                                    break
                                } finally {
                                    runCatching { codec.releaseOutputBuffer(out.index, false) }
                                }
                            }
                        }

                    var presentationTimeUs = 0L
                    try {
                        input.collect { chunk ->
                            if (!isActive) return@collect

                            // Wait for an input buffer to become available; the channel is
                            // closed when the codec reports an asynchronous error.
                            val inputIndex =
                                try {
                                    inputBufferAvailable.receive()
                                } catch (ex: ClosedReceiveChannelException) {
                                    throw CancellationException("Audio codec stopped", ex)
                                }
                            queueChunk(codec, inputIndex, chunk.data, presentationTimeUs)
                            presentationTimeUs += MediaFormats.Audio.FRAME_DURATION_MS * 1_000L
                        }
                    } finally {
                        drainJob.cancel()
                        inputBufferAvailable.close()
                        outputBufferAvailable.close()
                    }
                }.apply {
                    invokeOnCompletion {
                        releaseCodec(codec)
                        decoder = null
                    }
                }
    }

    actual fun stop() {
        decodeJob?.cancel()
        decodeJob = null
    }

    /**
     * Copies [data] into the codec's input buffer at [inputIndex] and queues it.
     * Throws [CancellationException] if the codec rejects the buffer or is in an
     * invalid state, so the surrounding decode loop unwinds cleanly instead of
     * propagating the failure as an unhandled exception.
     */
    private fun queueChunk(
        codec: MediaCodec,
        inputIndex: Int,
        data: ByteArray,
        presentationTimeUs: Long,
    ) {
        try {
            val inputBuffer = codec.getInputBuffer(inputIndex) ?: return
            inputBuffer.clear()
            val size = minOf(data.size, inputBuffer.remaining())
            inputBuffer.put(data, 0, size)
            codec.queueInputBuffer(inputIndex, 0, size, presentationTimeUs, 0)
        } catch (ex: MediaCodec.CodecException) {
            Logger.e(TAG, ex) { "Failed to queue audio input buffer; stopping decode loop" }
            throw CancellationException("Audio codec error", ex)
        } catch (ex: IllegalStateException) {
            Logger.w(TAG, ex) { "Audio codec in invalid state; stopping decode loop" }
            throw CancellationException("Audio codec invalid state", ex)
        }
    }

    private fun createAudioDecoder(
        inputBufferAvailable: Channel<Int>,
        outputBufferAvailable: Channel<OutputBufferInfo>,
        onCodecError: (MediaCodec.CodecException) -> Unit,
    ): MediaCodec {
        val format =
            MediaFormat
                .createAudioFormat(
                    MediaFormat.MIMETYPE_AUDIO_OPUS,
                    MediaFormats.Audio.SAMPLE_RATE,
                    MediaFormats.Audio.CHANNELS,
                ).apply {
                    setByteBuffer("csd-0", csd0)
                    setByteBuffer("csd-1", csd1)
                    setByteBuffer("csd-2", csd2)
                }

        val codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS)

        codec.setCallback(
            object : MediaCodec.Callback() {
                override fun onInputBufferAvailable(
                    mc: MediaCodec,
                    index: Int,
                ) {
                    inputBufferAvailable.trySend(index)
                }

                override fun onOutputBufferAvailable(
                    mc: MediaCodec,
                    index: Int,
                    info: MediaCodec.BufferInfo,
                ) {
                    if (info.size > 0) {
                        outputBufferAvailable.trySend(OutputBufferInfo(index, info.offset, info.size))
                    } else {
                        mc.releaseOutputBuffer(index, false)
                    }
                }

                override fun onError(
                    mc: MediaCodec,
                    e: MediaCodec.CodecException,
                ) {
                    onCodecError(e)
                }

                override fun onOutputFormatChanged(
                    mc: MediaCodec,
                    format: MediaFormat,
                ) {
                    Logger.d(TAG) { "Audio decoder output format changed: $format" }
                }
            },
        )

        codec.configure(format, null, null, 0)
        codec.start()
        return codec
    }

    private fun releaseCodec(codec: MediaCodec) {
        try {
            codec.stop()
            codec.release()
        } catch (ex: IllegalStateException) {
            Logger.w(TAG, ex) { "Error releasing decoder" }
        }
    }

    private data class OutputBufferInfo(
        val index: Int,
        val offset: Int,
        val size: Int,
    )

    private companion object {
        private val TAG = AudioDecoder::class

        /** Size of the OpusHead identification header for mono/stereo (mapping family 0). */
        private const val OPUS_HEAD_SIZE = 19

        /** Default Opus encoder pre-skip in samples (6.5 ms at 48 kHz). */
        private const val OPUS_PRE_SKIP_SAMPLES: Short = 312

        /** Seek pre-roll: 80 ms in nanoseconds, required by Android's Opus decoder. */
        private const val SEEK_PRE_ROLL_NS = 80_000_000L

        // CSD-0: plain 19-byte OpusHead (legacy format).
        // The framework delivers this as the first codec-config input buffer automatically.
        private val csd0: ByteBuffer by lazy {
            ByteBuffer.allocate(OPUS_HEAD_SIZE).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("OpusHead".toByteArray(Charsets.US_ASCII))
                put(1) // version
                put(MediaFormats.Audio.CHANNELS.toByte()) // channel count
                putShort(OPUS_PRE_SKIP_SAMPLES) // pre-skip in samples
                putInt(MediaFormats.Audio.SAMPLE_RATE) // input sample rate
                putShort(0) // output gain
                put(0) // channel mapping family
                flip()
            }
        }

        // CSD-1: codec delay (pre-skip) in nanoseconds, native-endian uint64
        private val csd1: ByteBuffer by lazy {
            ByteBuffer.allocate(8).order(ByteOrder.nativeOrder()).apply {
                putLong(OPUS_PRE_SKIP_SAMPLES.toLong() * 1_000_000_000L / MediaFormats.Audio.SAMPLE_RATE)
                flip()
            }
        }

        // CSD-2: seek pre-roll in nanoseconds, native-endian uint64
        private val csd2: ByteBuffer by lazy {
            ByteBuffer.allocate(8).order(ByteOrder.nativeOrder()).apply {
                putLong(SEEK_PRE_ROLL_NS)
                flip()
            }
        }
    }
}
