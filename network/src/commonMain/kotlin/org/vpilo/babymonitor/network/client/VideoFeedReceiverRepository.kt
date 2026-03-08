package org.vpilo.babymonitor.network.client

import org.vpilo.babymonitor.model.VideoFeedFlow
import org.vpilo.babymonitor.model.VideoFeedRepository
import org.vpilo.babymonitor.model.makeMutableVideoFeedFlow

class VideoFeedReceiverRepository : VideoFeedRepository {
    override val chunks: VideoFeedFlow = makeMutableVideoFeedFlow()
}
