package org.vpilo.babymonitor.model

import android.view.Surface
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

class AndroidServerVideoStream : AndroidVideoStream() {
    private val encoder = SurfaceSlot()

    override val isActive: Flow<Boolean> =
        combine(super.isActive, encoder.isOccupied) { viewfinder, encoder -> viewfinder || encoder }
            .distinctUntilChanged()

    /**
     * Registers [surface] as the encoder input, replacing any previously attached one.
     */
    fun attachEncoderSurface(surface: Surface) = encoder.offer(surface)

    /**
     * Detaches [surface], suspending until the consumer has released it, so the caller can then release it.
     */
    suspend fun detachEncoderSurface(surface: Surface) {
        encoder.withdraw(surface)
        encoder.awaitReleased(surface)
    }

    /**
     * Calls [attach] for each attached encoder Surface, and [detach] once it is detached or replaced.
     * A surface detached before [attach] could run is skipped, as its owner may already be releasing it.
     */
    suspend fun consumeEncoderSurface(
        attach: suspend (Surface) -> Unit,
        detach: suspend () -> Unit,
    ) = encoder.consume(attach, detach)
}
