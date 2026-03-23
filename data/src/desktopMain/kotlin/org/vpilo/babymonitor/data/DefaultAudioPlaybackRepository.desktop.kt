package org.vpilo.babymonitor.data

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.AudioFrameFlow
import org.vpilo.babymonitor.model.MediaFormats
import org.vpilo.babymonitor.model.repository.AudioPlaybackRepository
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine

internal actual class DefaultAudioPlaybackRepository actual constructor() : AudioPlaybackRepository {

    override suspend fun play(input: AudioFrameFlow) {
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
                Logger.d(TAG) { "Starting audio playback" }
                input.collect { chunk ->
                    if (!isActive) return@collect
                    audioLine.write(chunk, 0, chunk.size)
                }
            }
                .invokeOnCompletion {
                    Logger.d(TAG) { "Stopping audio playback: $it" }
                    audioLine.stop()
                    audioLine.close()
                }
        }
    }

    private companion object {
        private val TAG = DefaultAudioPlaybackRepository::class
    }
}

