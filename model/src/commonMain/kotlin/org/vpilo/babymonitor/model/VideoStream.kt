package org.vpilo.babymonitor.model

import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

typealias OpaqueVideoStream = VideoStream<*>

abstract class VideoStream<T> {
    private val mutableFrameSize = MutableStateFlow(IntSize.Zero)
    private val mutableFrameCounter = MutableStateFlow(0L)
    private val mutableRotation = MutableStateFlow(0)

    /** Resolution of the video stream frames.
     * Only available during streaming, and [IntSize.Zero] otherwise.
     */
    val frameSize: StateFlow<IntSize> = mutableFrameSize.asStateFlow()

    /** Monotonically increasing frame counter.
     * Used to calculate FPS counts.
     */
    val frameCounter: StateFlow<Long> = mutableFrameCounter.asStateFlow()

    /**
     * Camera-coordinates rotation in degrees, snapped to {0, 90, 180, 270}.
     * Consumers must rotate the frame by this amount to display it upright.
     */
    val rotation: StateFlow<Int> = mutableRotation.asStateFlow()

    /** Primary video streaming output surface. */
    abstract val surface: Flow<T?>

    /**
     * Whether the streaming should be started or stopped.
     * This is true while at least one consumer is collecting the feed.
     */
    abstract val isActive: Flow<Boolean>

    fun setRotation(rotation: Int) {
        mutableRotation.value = rotation
    }

    fun setFrameSize(
        width: Int,
        height: Int,
    ) {
        mutableFrameSize.value = IntSize(width, height)
    }

    fun signalFrameRendered() {
        mutableFrameCounter.value += 1
    }
}
