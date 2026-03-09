package org.vpilo.babymonitor.model

/**
 * An opaque encoded chunk produced by a video encoder.
 *
 * These chunks are raw Annex-B H.264 NAL units (no container).
 * The metadata flags allow late-joining clients to start decoding immediately
 * by waiting for the next [isKeyFrame] preceded by any [isCodecConfig] chunks.
 */
data class EncodedStreamChunk(
    /** Raw Annex-B H.264 NAL unit data. */
    val data: ByteArray,
    /** `true` when this is an IDR frame — a safe point to start decoding. */
    val isKeyFrame: Boolean,
    /** `true` when this carries SPS/PPS — must be sent before the first keyframe. */
    val isCodecConfig: Boolean,
    /** Presentation timestamp in microseconds. */
    val timestampUs: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncodedStreamChunk) return false
        return isKeyFrame == other.isKeyFrame &&
                isCodecConfig == other.isCodecConfig &&
                timestampUs == other.timestampUs &&
                data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + isKeyFrame.hashCode()
        result = 31 * result + isCodecConfig.hashCode()
        result = 31 * result + timestampUs.hashCode()
        return result
    }
}
