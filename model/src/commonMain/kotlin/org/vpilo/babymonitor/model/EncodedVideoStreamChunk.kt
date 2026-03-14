package org.vpilo.babymonitor.model

import kotlinx.serialization.Serializable

/**
 * An encoded chunk of video.
 * Video chunks are raw Annex-B H.264 NAL units (no container).
 *
 * The [isKeyFrame] flag allows clients to start decoding without errors by waiting for the next key frame.
 */
@Serializable
data class EncodedVideoStreamChunk(
    /** Raw encoded data (H.264 NAL units). */
    val data: ByteArray,
    /** `true` when this is an IDR frame. */
    val isKeyFrame: Boolean,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncodedVideoStreamChunk) return false
        return isKeyFrame == other.isKeyFrame &&
                data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + isKeyFrame.hashCode()
        return result
    }
}
