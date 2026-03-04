package org.vpilo.babymonitor.model

interface VideoFeedRepository : AutoCloseable {
    val frames: FrameFlow
    val samples: SampleFlow

    val isOpen: Boolean

    fun start()

    fun stop()

    override fun close() {
        stop()
    }
}
