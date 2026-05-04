package org.vpilo.babymonitor.filters

import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import org.vpilo.babymonitor.model.AudioFrameFlow
import org.vpilo.babymonitor.model.MediaFormats
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Drops the [input] audio stream while its RMS amplitude falls below a certain threshold, defined by [sensitivityLevel].
 *
 * The [sensitivityLevel] is in a range from the least sensitive 0, corresponding to ~-20 dBFS, up to the most sensitive
 * [MediaFormats.Audio.MAX_NOISE_SENSITIVITY_LEVEL], ~-60 dBFS.
 */
class AudioSilenceFilter(
    input: AudioFrameFlow,
    sensitivityLevel: Int = MediaFormats.Audio.DEFAULT_NOISE_SENSITIVITY_LEVEL,
) : AudioStreamFilter() {
    var sensitivityLevel: Int = clamp(sensitivityLevel)
        set(value) {
            field = clamp(value)
            threshold = computeThreshold(field)
        }

    private var threshold: Double = computeThreshold(this.sensitivityLevel)

    private var silentFramesDetected = 0
    private var haveSilence = false

    override val output: AudioFrameFlow =
        input
            .onEach {
                silentFramesDetected = if (isSilent(it)) silentFramesDetected + 1 else 0
                haveSilence = silentFramesDetected > SILENCE_FRAMES_COUNT
            }.filter {
                !haveSilence
            }

    private fun isSilent(frame: ByteArray): Boolean {
        if (frame.size < BYTES_PER_SAMPLE) return false

        val sampleCount = frame.size / BYTES_PER_SAMPLE
        var sumSq = 0.0
        for (i in 0 until sampleCount) {
            val sample = decodeSample(frame, i * BYTES_PER_SAMPLE)
            sumSq += (sample * sample).toDouble()
        }
        return sqrt(sumSq / sampleCount) < threshold
    }

    private fun decodeSample(
        frame: ByteArray,
        offset: Int,
    ): Int {
        var raw = 0
        if (MediaFormats.Audio.BIG_ENDIAN) {
            for (i in 0 until BYTES_PER_SAMPLE) {
                raw = (raw shl 8) or (frame[offset + i].toInt() and 0xFF)
            }
        } else {
            for (i in BYTES_PER_SAMPLE - 1 downTo 0) {
                raw = (raw shl 8) or (frame[offset + i].toInt() and 0xFF)
            }
        }
        return if (MediaFormats.Audio.SIGNED) {
            // Sign-extend from SAMPLE_SIZE_BITS up to Int width.
            val shift = Int.SIZE_BITS - MediaFormats.Audio.SAMPLE_SIZE_BITS
            (raw shl shift) shr shift
        } else {
            raw - UNSIGNED_MIDPOINT
        }
    }

    private companion object {
        private const val BYTES_PER_SAMPLE = MediaFormats.Audio.SAMPLE_SIZE_BITS / 8

        private const val UNSIGNED_MIDPOINT: Int = 1 shl (MediaFormats.Audio.SAMPLE_SIZE_BITS - 1)

        private const val SILENCE_FRAMES_COUNT = MediaFormats.Audio.NOISE_DURATION_MS / MediaFormats.Audio.FRAME_DURATION_MS

        private val MAX_AMPLITUDE: Double =
            if (MediaFormats.Audio.SIGNED) {
                (1L shl (MediaFormats.Audio.SAMPLE_SIZE_BITS - 1)).toDouble()
            } else {
                ((1L shl MediaFormats.Audio.SAMPLE_SIZE_BITS) - 1L).toDouble() / 2.0
            }

        private fun clamp(level: Int): Int = level.coerceIn(0, MediaFormats.Audio.MAX_NOISE_SENSITIVITY_LEVEL)

        // Levels map linearly from -20 dBFS (level 0) to -60 dBFS (max level). Step size depends on max sensitivity.
        private fun computeThreshold(level: Int): Double {
            val dbFs = -20.0 - level * (40.0 / MediaFormats.Audio.MAX_NOISE_SENSITIVITY_LEVEL)
            return MAX_AMPLITUDE * (10.0).pow(dbFs / 20.0)
        }
    }
}
