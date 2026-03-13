package org.vpilo.babymonitor.codec

import kotlinx.coroutines.flow.asSharedFlow
import org.vpilo.babymonitor.model.MutableStreamingVideoFlow
import org.vpilo.babymonitor.model.StreamingVideoFlow
import org.vpilo.babymonitor.model.repository.StreamingVideoSenderRepository
import org.vpilo.babymonitor.model.makeMutableStreamingVideoFlow

class StreamingVideoReceiverRepository : StreamingVideoSenderRepository {
    val collector: MutableStreamingVideoFlow = makeMutableStreamingVideoFlow()
    override val chunks: StreamingVideoFlow = collector.asSharedFlow()
}
