package org.vpilo.babymonitor.model

/**
 * An encoded chunk of audio.
 * Audio chunks are raw AAC frames (ADTS on Desktop, raw on Android).
 * Each frame is a self-contained unit of audio data, and can be decoded independently.
 */
data class EncodedAudioStreamChunk(
    /** Encoded data (AAC format). */
    val data: ByteArray,
    /** `true` when this carries codec configuration. FIXME check if needed on android, it's not needed on desktop */
    val isCodecConfig: Boolean,
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncodedAudioStreamChunk) return false
        return isCodecConfig == other.isCodecConfig &&
                data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + isCodecConfig.hashCode()
        return result
    }
}
