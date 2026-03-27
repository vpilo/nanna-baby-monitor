package org.vpilo.babymonitor.model.repository.ktx

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * Reacts to the active state of a [MutableSharedFlow].
 * Invokes [onActive] when the first subscriber appears, and [onInactive] when the last subscriber disappears.
 * The [onInactive] callback is not invoked if the flow had never any subscribers.
 */
fun <T> MutableSharedFlow<T>.reactor(
    scope: CoroutineScope,
    onActive: () -> Unit,
    onInactive: () -> Unit,
): Job {
    var neverActivated = true
    return subscriptionCount
        .map { count -> count > 0 }
        .distinctUntilChanged()
        .onEach { isActive ->
            when {
                isActive -> {
                    neverActivated = false
                    onActive()
                }

                neverActivated -> {
                    // Do nothing
                }

                else -> {
                    onInactive()
                }
            }
        }.launchIn(scope)
}
