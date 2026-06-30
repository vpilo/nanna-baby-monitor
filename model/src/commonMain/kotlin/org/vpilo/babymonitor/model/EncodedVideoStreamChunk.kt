package org.vpilo.babymonitor.model

import kotlinx.serialization.Serializable

/**
 * An encoded chunk of video.
 * Video chunks are raw Annex-B H.264 NAL units (no container).
 *
 * The [isKeyFrame] flag allows clients to start decoding without errors by waiting for the next key frame.
 *
 * The [rotation] is camera-coordinates rotation in degrees ({0, 90, 180, 270}) that the consumer
 * must apply to display the frame upright.
 */
@Serializable
data class EncodedVideoStreamChunk(
    /** Raw encoded data (H.264 NAL units). */
    val data: ByteArray,
    /** `true` when this is an IDR frame. */
    val isKeyFrame: Boolean,
    val frameWidth: Int,
    val frameHeight: Int,
    /** Rotation in degrees, snapped to {0, 90, 180, 270}. */
    val rotation: Int = 0,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncodedVideoStreamChunk) return false
        return isKeyFrame == other.isKeyFrame &&
            rotation == other.rotation &&
            data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + isKeyFrame.hashCode()
        result = 31 * result + rotation
        return result
    }
}
