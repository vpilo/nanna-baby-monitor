package org.vpilo.babymonitor.app

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.AudioFrameFlow
import org.vpilo.babymonitor.model.MediaFormats

private const val TAG = "playAudioStream"

actual suspend fun playAudioStream(input: AudioFrameFlow) {
    val channelConfig = when (MediaFormats.Audio.CHANNELS) {
        1 -> AudioFormat.CHANNEL_OUT_MONO
        else -> AudioFormat.CHANNEL_OUT_STEREO
    }
    val minBufferSize = AudioTrack.getMinBufferSize(
        MediaFormats.Audio.SAMPLE_RATE,
        channelConfig,
        AudioFormat.ENCODING_PCM_16BIT,
    )

    val audioTrack = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(MediaFormats.Audio.SAMPLE_RATE)
                .setChannelMask(channelConfig)
                .build(),
        )
        .setBufferSizeInBytes(minBufferSize * 2)
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()

    coroutineScope {
        launch {
            Logger.d(TAG) { "Starting audio playback" }
            input.collect { chunk ->
                if (!isActive) return@collect
                audioTrack.write(chunk, 0, chunk.size)
            }
        }
            .invokeOnCompletion {
                Logger.d(TAG) { "Stopping audio playback: $it" }
                audioTrack.stop()
                audioTrack.release()
            }
    }
}
