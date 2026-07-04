package org.vpilo.babymonitor.model

import android.view.Surface
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

sealed class AndroidVideoStream : VideoStream<Surface>() {
    private val mutableSurface = MutableStateFlow<Surface?>(null)
    override val surface: Flow<Surface?> = mutableSurface.asStateFlow()
    override val isActive: Flow<Boolean> = mutableSurface.map { it != null }

    /**
     * Registers [surface] as the current output, replacing any previously attached surface.
     *
     * Overlapping surface lifecycles are expected: a new [android.view.TextureView] can produce its
     * surface before the outgoing one is destroyed (e.g. during a navigation transition), because
     * this stream is a long-lived singleton shared across surface producers.
     */
    fun attachSurface(surface: Surface) {
        mutableSurface.value = surface
    }

    /**
     * Detaches [surface] only if it is still the current one, so a stale destroy from a torn-down
     * producer cannot clobber a newer surface that already replaced it.
     */
    fun detachSurface(surface: Surface) {
        mutableSurface.compareAndSet(surface, null)
    }
}
