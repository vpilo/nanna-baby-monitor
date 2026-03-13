package org.vpilo.babymonitor.codec

import kotlinx.coroutines.flow.asSharedFlow
import org.vpilo.babymonitor.model.MutableStreamingAudioFlow
import org.vpilo.babymonitor.model.StreamingAudioFlow
import org.vpilo.babymonitor.model.repository.StreamingAudioSenderRepository
import org.vpilo.babymonitor.model.makeMutableStreamingAudioFlow

class StreamingAudioReceiverRepository : StreamingAudioSenderRepository {
    val collector: MutableStreamingAudioFlow = makeMutableStreamingAudioFlow()
    override val chunks: StreamingAudioFlow = collector.asSharedFlow()
}
