package org.vpilo.babymonitor.network.client

import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.MutableFrameFlow
import org.vpilo.babymonitor.model.MutableSampleFlow
import org.vpilo.babymonitor.model.VideoFeedRepository
import org.vpilo.babymonitor.model.makeMutableFrameFlow
import org.vpilo.babymonitor.model.makeMutableSampleFlow

class VideoFeedReceiverRepository : VideoFeedRepository {
    override val frames: MutableFrameFlow = makeMutableFrameFlow()
    override val samples: MutableSampleFlow = makeMutableSampleFlow()

    override val isOpen: Boolean = true

    override fun start() {
        Logger.w(this::class) { "Starting VideoFeedReceiverRepository." }
    }

    override fun stop() {
        Logger.w(this::class) { "Stopping VideoFeedReceiverRepository." }
    }
}
