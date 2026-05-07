package org.vpilo.babymonitor.model.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.repository.ktx.reactor
import kotlin.reflect.KClass

/**
 * Base class for a repository that manages a shared resource, such as a camera.
 *
 * This assumes the shared resource generates a flow of [T] objects when in use.
 * [start] when the first subscriber starts using the [collector], and [stop] is called when the last subscriber stops using it.
 *
 * The [bufferCapacity] determines the [collector]'s buffer, and the [onBufferOverflow] strategy determines how to handle buffer overflows,
 * e.g. slow subscribers.
 */
abstract class SharedResourceHolder<T>(
    protected val coroutineDispatcher: CoroutineDispatcher = Dispatchers.Default,
    bufferCapacity: Int = 0,
    onBufferOverflow: BufferOverflow = BufferOverflow.DROP_OLDEST,
) {
    protected val coroutineScope = CoroutineScope(coroutineDispatcher)

    protected val collector: MutableSharedFlow<T> =
        MutableSharedFlow(0, bufferCapacity, onBufferOverflow = onBufferOverflow)

    init {
        collector.reactor(coroutineScope, onActive = ::onActive, onInactive = ::onInactive)
    }

    val isActive: Flow<Boolean> =
        collector.subscriptionCount
            .map { it > 0 }
            .distinctUntilChanged()

    protected val isActiveNow: Boolean
        get() = collector.subscriptionCount.value > 0

    protected abstract fun start()

    protected abstract fun stop()

    private fun onActive() {
        Logger.d(TAG) { "Starting" }
        start()
    }

    private fun onInactive() {
        Logger.d(TAG) { "Stopping" }
        stop()
    }

    @Suppress("VariableNaming", "PropertyName", "ktlint:standard:property-naming")
    open val TAG: KClass<*> = this::class
}
