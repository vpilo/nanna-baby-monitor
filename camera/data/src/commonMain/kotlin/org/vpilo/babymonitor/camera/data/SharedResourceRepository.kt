package org.vpilo.babymonitor.camera.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.ktx.reactor
import kotlin.reflect.KClass

abstract class SharedResourceRepository<T>(
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

    val isActive: Boolean
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

    abstract val TAG: KClass<*>
}
