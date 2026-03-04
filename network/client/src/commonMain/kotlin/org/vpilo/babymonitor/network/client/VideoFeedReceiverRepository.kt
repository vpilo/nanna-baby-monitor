package org.vpilo.babymonitor.network.client

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.CameraFrameData
import org.vpilo.babymonitor.model.FrameFlow
import org.vpilo.babymonitor.model.MutableFrameFlow
import org.vpilo.babymonitor.model.MutableSampleFlow
import org.vpilo.babymonitor.model.SampleFlow
import org.vpilo.babymonitor.model.VideoFeedRepository
import org.vpilo.babymonitor.model.makeMutableFrameFlow
import org.vpilo.babymonitor.model.makeMutableSampleFlow

class VideoFeedReceiverRepository: VideoFeedRepository {

    override val frames: FrameFlow = NetworkDataCollector.frameCollector.asSharedFlow()
    override val samples: SampleFlow = NetworkDataCollector.sampleCollector.asSharedFlow()

    override val isOpen: Boolean = true

    override fun start() {
        Logger.w(this::class) { "Starting VideoFeedReceiverRepository." }
    }

    override fun stop() {
        Logger.w(this::class) { "Stopping VideoFeedReceiverRepository." }
    }
}
