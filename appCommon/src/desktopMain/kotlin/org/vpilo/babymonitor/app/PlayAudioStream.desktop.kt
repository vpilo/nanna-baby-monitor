package org.vpilo.babymonitor.app

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.AudioFrameFlow
import org.vpilo.babymonitor.model.MediaFormats
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine

private const val TAG = "playAudioStream"

actual suspend fun playAudioStream(input: AudioFrameFlow) {
    runCatching {
        val audioFormat = AudioFormat(
            MediaFormats.Audio.SAMPLE_RATE.toFloat(),
            MediaFormats.Audio.SAMPLE_SIZE_BITS,
            MediaFormats.Audio.CHANNELS,
            MediaFormats.Audio.SIGNED,
            MediaFormats.Audio.BIG_ENDIAN,
        )
        val lineInfo = DataLine.Info(SourceDataLine::class.java, audioFormat)
        val audioLine = AudioSystem.getLine(lineInfo) as SourceDataLine
        audioLine.open(audioFormat)
        audioLine.start()

        coroutineScope {
            launch {
                input.collect { chunk ->
                    Logger.d(TAG) { "Starting audio playback" }
                    if (!isActive) return@collect
                    audioLine.write(chunk, 0, chunk.size)
                }
            }
                .invokeOnCompletion {
                    Logger.d(TAG) { "Stopping audio playback" }
                    audioLine.stop()
                    audioLine.close()
                }
        }
    }.onFailure {
        Logger.e(TAG) { "Failed to play audio stream: ${it.message}" }
    }
}
