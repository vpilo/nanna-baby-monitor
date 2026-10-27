package org.vpilo.babymonitor.model

import android.view.Surface
import kotlinx.coroutines.flow.Flow

sealed class AndroidVideoStream : VideoStream<Surface>() {
    private val viewfinder = SurfaceSlot()
    override val surface: Flow<Surface?> = viewfinder.requested
    override val isActive: Flow<Boolean> = viewfinder.isOccupied

    /**
     * Sets [surface] as the output, replacing the previous one.
     * A new [android.view.TextureView] can create its surface before the old one is destroyed, e.g. during a navigation transition.
     */
    fun attachSurface(surface: Surface) = viewfinder.offer(surface)

    /**
     * Removes [surface] if it is still the output, so a late call from an old producer does not remove its replacement.
     * Does not wait for the consumer: the system releases the surface anyway, and a late consumer only gets errors.
     */
    fun detachSurface(surface: Surface) = viewfinder.withdraw(surface)

    /**
     * Calls [attach] with each output surface, and [detach] when it is removed or replaced.
     * [attach] may suspend while it uses the surface, and is canceled when the surface is removed.
     */
    suspend fun consumeSurface(
        attach: suspend (Surface) -> Unit,
        detach: suspend () -> Unit,
    ) = viewfinder.consume(attach, detach)
}
