package org.vpilo.babymonitor.model

/**
 * An encoded chunk of video.
 * Video chunks are raw Annex-B H.264 NAL units (no container).
 *
 * The metadata flags allow late-joining clients to start decoding immediately by waiting for the next [isKeyFrame] preceded by any
 * [isCodecConfig] chunks.
 */
data class EncodedVideoStreamChunk(
    /** Raw encoded data (H.264 NAL units). */
    val data: ByteArray,
    /** `true` when this is an IDR frame. */
    val isKeyFrame: Boolean,
    /** `true` when this carries codec configuration (SPS/PPS). */
    val isCodecConfig: Boolean,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncodedVideoStreamChunk) return false
        return isKeyFrame == other.isKeyFrame &&
                isCodecConfig == other.isCodecConfig &&
                data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + isKeyFrame.hashCode()
        result = 31 * result + isCodecConfig.hashCode()
        return result
    }
}
