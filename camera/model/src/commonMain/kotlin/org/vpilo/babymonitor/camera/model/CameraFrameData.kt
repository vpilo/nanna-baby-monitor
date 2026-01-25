package org.vpilo.babymonitor.camera.model

import kotlin.time.Instant

data class CameraFrameData(
    val data: ByteArray,
    val width: Int,
    val height: Int,
    val rotation: CameraImageRotation,
    val format: CameraImageFormat,
    val timestamp: Instant,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CameraFrameData

        if (width != other.width) return false
        if (height != other.height) return false
        if (rotation != other.rotation) return false
        if (format != other.format) return false
        if (timestamp != other.timestamp) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + rotation.hashCode()
        result = 31 * result + format.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}
