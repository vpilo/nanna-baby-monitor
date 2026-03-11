package org.vpilo.babymonitor.network.client

import kotlinx.coroutines.flow.asSharedFlow
import org.vpilo.babymonitor.model.MutableStreamingVideoFlow
import org.vpilo.babymonitor.model.StreamingVideoFlow
import org.vpilo.babymonitor.model.StreamingVideoRepository
import org.vpilo.babymonitor.model.makeMutableStreamingVideoFlow

class StreamingVideoReceiverRepository : StreamingVideoRepository {
    internal val collector: MutableStreamingVideoFlow = makeMutableStreamingVideoFlow()
    override val chunks: StreamingVideoFlow = collector.asSharedFlow()
}
