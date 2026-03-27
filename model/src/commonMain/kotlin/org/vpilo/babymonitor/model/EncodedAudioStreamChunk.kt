package org.vpilo.babymonitor.model

import kotlinx.serialization.Serializable

/**
 * An encoded chunk of audio.
 * Audio chunks are raw Opus frames.
 * Each frame is a self-contained unit of audio data, and can be decoded independently.
 */
@Serializable
data class EncodedAudioStreamChunk(
    /** Encoded data (Opus format). */
    val data: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EncodedAudioStreamChunk

        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int = data.contentHashCode()
}
