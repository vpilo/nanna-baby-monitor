package org.vpilo.babymonitor.model

/**
 * An opaque encoded chunk produced by a media encoder.
 *
 * Video chunks are raw Annex-B H.264 NAL units (no container).
 * Audio chunks are raw AAC frames (ADTS on Desktop, raw on Android).
 *
 * The metadata flags allow late-joining clients to start decoding immediately
 * by waiting for the next [isKeyFrame] preceded by any [isCodecConfig] chunks.
 */
data class EncodedStreamChunk(
    /** Raw encoded data (H.264 NAL units for video, AAC frames for audio). */
    val data: ByteArray,
    /** `true` when this is an IDR frame (video) or a standalone audio frame — a safe point to start decoding. */
    val isKeyFrame: Boolean,
    /** `true` when this carries codec configuration (SPS/PPS for video, AudioSpecificConfig for audio). */
    val isCodecConfig: Boolean,
    /** `true` when this chunk contains audio data, `false` for video. */
    val isAudio: Boolean = false,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncodedStreamChunk) return false
        return isKeyFrame == other.isKeyFrame &&
                isCodecConfig == other.isCodecConfig &&
                isAudio == other.isAudio &&
                data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + isKeyFrame.hashCode()
        result = 31 * result + isCodecConfig.hashCode()
        result = 31 * result + isAudio.hashCode()
        return result
    }
}
