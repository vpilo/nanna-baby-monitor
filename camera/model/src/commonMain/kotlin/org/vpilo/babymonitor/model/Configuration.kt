package org.vpilo.babymonitor.model

object Configuration {

    /**
     * Maximum amount of unprocessed frames to keep queued before starting to drop older ones.
     */
    const val MAX_FRAME_BUFFER_SIZE: Int = 30

    /**
     * Maximum amount of unprocessed audio samples to keep queued before starting to drop older ones.
     */
    const val MAX_SAMPLE_BUFFER_SIZE: Int = 11025

    /**
     * Maximum amount of compressed video streaming chunks to keep queued before starting to drop older ones.
     */
    const val MAX_VIDEO_STREAM_BUFFER_SIZE: Int = 64
}
