package org.vpilo.babymonitor.camera.data

import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.FrameFlow
import org.vpilo.babymonitor.model.SampleFlow
import org.vpilo.babymonitor.model.VideoFeedRepository

internal class CameraFeedRepository(
) : VideoFeedRepository {

    override val frames: FrameFlow = CameraInterface.frames
    override val samples: SampleFlow = CameraInterface.samples

    /**
     * Reference count of users of the camera feed.
     * This is due to the repository being used both by the UI and the network layer independently.
     */
    @Volatile
    private var userRefCount = 0

    override val isOpen: Boolean
        get() = CameraInterface.isStarted()

    override fun start() {
        if (userRefCount++ == 0) {
            CameraInterface.start()
        }

        Logger.d(TAG) { "start() - refcount $userRefCount" }
    }

    override fun stop() {
        if (--userRefCount == 0) {
            CameraInterface.stop()
        }

        Logger.d(TAG) { "stop() - refcount $userRefCount" }
    }

    private companion object {
        private val TAG = CameraFeedRepository::class
    }
}
