package org.vpilo.babymonitor.network.client

import kotlinx.coroutines.flow.asSharedFlow
import org.vpilo.babymonitor.model.MutableStreamingAudioFlow
import org.vpilo.babymonitor.model.MutableStreamingVideoFlow
import org.vpilo.babymonitor.model.StreamingAudioFlow
import org.vpilo.babymonitor.model.StreamingAudioRepository
import org.vpilo.babymonitor.model.StreamingVideoFlow
import org.vpilo.babymonitor.model.StreamingVideoRepository
import org.vpilo.babymonitor.model.makeMutableStreamingAudioFlow
import org.vpilo.babymonitor.model.makeMutableStreamingVideoFlow

class StreamingAudioReceiverRepository : StreamingAudioRepository {
    internal val collector: MutableStreamingAudioFlow = makeMutableStreamingAudioFlow()
    override val chunks: StreamingAudioFlow = collector.asSharedFlow()
}
