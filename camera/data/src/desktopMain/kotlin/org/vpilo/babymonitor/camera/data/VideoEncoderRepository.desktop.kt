package org.vpilo.babymonitor.camera.data

import org.vpilo.babymonitor.model.Configuration
import org.vpilo.babymonitor.model.VideoFeedFlow
import org.vpilo.babymonitor.model.VideoFeedRepository
import kotlin.reflect.KClass

actual class VideoEncoderRepository : VideoFeedRepository,
    SharedResourceRepository<ByteArray>(
        bufferCapacity = Configuration.MAX_VIDEO_STREAM_BUFFER_SIZE,
    ) {

    override val chunks: VideoFeedFlow
        get() = TODO("Not yet implemented")

    override fun start() {
        TODO("Not yet implemented")
    }

    override fun stop() {
        TODO("Not yet implemented")
    }

    override val TAG: KClass<*> = VideoEncoderRepository::class
}
