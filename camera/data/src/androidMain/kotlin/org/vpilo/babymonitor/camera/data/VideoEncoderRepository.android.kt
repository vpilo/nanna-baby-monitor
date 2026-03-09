package org.vpilo.babymonitor.camera.data

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import org.vpilo.babymonitor.android.service.AndroidService
import org.vpilo.babymonitor.android.service.AndroidServiceRegistry
import org.vpilo.babymonitor.model.Configuration
import org.vpilo.babymonitor.model.VideoFeedFlow
import org.vpilo.babymonitor.model.VideoFeedRepository
import kotlin.reflect.KClass

actual class VideoEncoderRepository : VideoFeedRepository,
    SharedResourceRepository<ByteArray>(
        bufferCapacity = Configuration.MAX_VIDEO_STREAM_BUFFER_SIZE,
    ), AndroidService {

    override val chunks: VideoFeedFlow
        get() = TODO("Not yet implemented")

    override fun onServiceStarted(context: Context, lifecycleOwner: LifecycleOwner) {
        TODO("Not yet implemented")
    }

    override fun onServiceStopped() {
        TODO("Not yet implemented")
    }

    override fun start() {
        AndroidServiceRegistry.register(this)
    }

    override fun stop() {
        AndroidServiceRegistry.unregister(this)
    }

    override val TAG: KClass<*> = VideoEncoderRepository::class
}
