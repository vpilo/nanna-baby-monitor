package org.vpilo.babymonitor.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.common.Logger

abstract class AppViewModel<A, S, E>(
    private val initialState: S,
    scope: CoroutineScope? = null,
) : ViewModel() {

    protected val TAG = this::class

    protected val vmScope: CoroutineScope = scope ?: viewModelScope

    private var vmActionsChannel: Channel<A> = Channel(capacity = Channel.BUFFERED)
    private var actionsJob: Job? = null

    private val _vmStateFlow: MutableStateFlow<S> = MutableStateFlow(initialState)
    val stateFlow: StateFlow<S> by lazy {
        _vmStateFlow
            .onSubscription {
                Logger.d(TAG) { "Subscribed" }
                actionsJob?.cancel()
                actionsJob = vmScope.launch {
                    vmActionsChannel.receiveAsFlow().collect { action ->
                        Logger.d(TAG) { "Received action: $action" }
                        onAction(action)
                    }
                }
                vmActionsChannel = Channel(capacity = Channel.BUFFERED)

                object : SubscriptionScope {
                    override fun <T> Flow<T>.subscribe(collector: suspend (value: T) -> Unit) {
                        vmScope.launch { this@subscribe.collect(collector) }
                    }
                }.onSubscribed()
            }.onCompletion {
                Logger.d(TAG) { "Unsubscribed" }
                vmActionsChannel.cancel()
                actionsJob?.cancel()
                actionsJob = null
                onUnsubscribed()
            }.stateIn(
                vmScope,
                SharingStarted.Lazily,
                initialState,
            )
    }
    protected val state: S
        get() = _vmStateFlow.value

    private val _vmEffectsChannel: Channel<E> = Channel(capacity = Channel.BUFFERED)
    val effectsFlow: Flow<E> = _vmEffectsChannel.receiveAsFlow()

    protected interface SubscriptionScope {
        /**
         * Subscribe this [Flow], collecting its values in [collector].
         * Automatically cancels the subscription when the [AppViewModel] is unsubscribed.
         */
        fun <T> Flow<T>.subscribe(collector: suspend (value: T) -> Unit)
    }

    protected open fun SubscriptionScope.onSubscribed() {
        /* Nothing gets subscribed */
    }

    protected open fun onUnsubscribed() {
        /* Nothing gets unsubscribed */
    }

    open fun onAction(action: A) {
        Logger.w(TAG) { "Received unhandled action: $action" }
    }

    protected fun A.sendAction() {
        Logger.d(TAG) { "Send action: $this" }
        vmActionsChannel
            .trySend(this)
            .onFailure { ex ->
                Logger.e(TAG, ex) { "Unable to send action: $this (${ex?.message ?: ex?.let { it::class.simpleName }})" }
            }
    }

    protected fun S.update() {
        check(this !is Unit) { "Cannot update a Unit VM state" }
        Logger.d(TAG) { "Updated state: $this" }
        _vmStateFlow.update { this }
    }

    protected fun E.sendEffect() {
        check(this !is Unit) { "Cannot send a Unit VM effect" }
        Logger.d(TAG) { "Send effect: $this" }
        vmScope.launch {
            _vmEffectsChannel.send(this@sendEffect)
        }
    }
}
