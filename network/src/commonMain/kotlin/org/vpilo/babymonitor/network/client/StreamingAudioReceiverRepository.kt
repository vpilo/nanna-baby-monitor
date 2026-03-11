package org.vpilo.babymonitor.network.client

import kotlinx.coroutines.flow.asSharedFlow
import org.vpilo.babymonitor.model.MutableStreamingAudioFlow
import org.vpilo.babymonitor.model.StreamingAudioFlow
import org.vpilo.babymonitor.model.repository.StreamingAudioRepository
import org.vpilo.babymonitor.model.makeMutableStreamingAudioFlow

class StreamingAudioReceiverRepository : StreamingAudioRepository {
    internal val collector: MutableStreamingAudioFlow = makeMutableStreamingAudioFlow()
    override val chunks: StreamingAudioFlow = collector.asSharedFlow()
}
