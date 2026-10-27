package org.vpilo.babymonitor.model

import android.view.Surface
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * Makes it safe to transfer a [Surface] from its producer to a single consumer.
 * The producer offers and withdraws surfaces; the consumer claims the current one, and releases it once it is withdrawn or replaced. A
 * surface stays claimed until the consumer has released it, so its owner can wait for that before destroying it.
 */
internal class SurfaceSlot {
    private val state = MutableStateFlow(State())

    val requested: Flow<Surface?> =
        state
            .map { it.requested }
            .distinctUntilChanged()

    /**
     * Whether a surface is offered, or is still claimed by the consumer.
     */
    val isOccupied: Flow<Boolean> =
        state
            .map { it.requested != null || it.consumed != null }
            .distinctUntilChanged()

    /**
     * Offers [surface], replacing any previously offered one.
     */
    fun offer(surface: Surface) {
        state.update { it.copy(requested = surface) }
    }

    /**
     * Withdraws [surface] if it is still offered, so a late call does not withdraw its replacement.
     */
    fun withdraw(surface: Surface) {
        state.update { if (it.requested === surface) it.copy(requested = null) else it }
    }

    /**
     * Suspends until the consumer no longer uses [surface].
     */
    suspend fun awaitReleased(surface: Surface) {
        state.first { it.consumed !== surface }
    }

    /**
     * Calls [attach] for each offered surface, and [detach] once it is withdrawn or replaced.
     * [attach] may suspend for as long as it uses the surface: it is canceled when the surface goes away. A surface withdrawn before
     * [attach] could run is skipped, as its owner may already be releasing it.
     */
    suspend fun consume(
        attach: suspend (Surface) -> Unit,
        detach: suspend () -> Unit,
    ) {
        requested.collectLatest { surface ->
            if (surface == null) return@collectLatest
            val previous = state.getAndUpdate { if (it.requested === surface) it.copy(consumed = surface) else it }
            if (previous.requested !== surface) return@collectLatest

            try {
                attach(surface)
                awaitCancellation()
            } finally {
                withContext(NonCancellable) {
                    detach()
                    state.update { it.copy(consumed = null) }
                }
            }
        }
    }

    private data class State(
        val requested: Surface? = null,
        val consumed: Surface? = null,
    )
}
