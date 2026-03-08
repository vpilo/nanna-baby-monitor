package org.vpilo.babymonitor.model

object Configuration {

    /**
     * Maximum amount of unprocessed frames to keep queued before starting to drop older ones.
     */
    const val MAX_FRAME_BUFFER_SIZE: Int = 30
}
