package org.vpilo.babymonitor.model.viewmodel

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
    @Suppress("VariableNaming", "ktlint:standard:property-naming", "PropertyName")
    protected val TAG = this::class

    protected val vmScope: CoroutineScope = scope ?: viewModelScope

    private var internalActionsChannel: Channel<A> = Channel(capacity = Channel.BUFFERED)
    private var actionsJob: Job? = null

    private val internalStateFlow: MutableStateFlow<S> = MutableStateFlow(initialState)
    val stateFlow: StateFlow<S> by lazy {
        internalStateFlow
            .onSubscription {
                Logger.d(TAG) { "Subscribed" }
                actionsJob?.cancel()
                internalActionsChannel = Channel(capacity = Channel.BUFFERED)
                actionsJob =
                    vmScope.launch {
                        internalActionsChannel.receiveAsFlow().collect { action ->
                            Logger.d(TAG) { "Received action: $action" }
                            onAction(action)
                        }
                    }

                object : SubscriptionScope {
                    override fun <T> Flow<T>.subscribe(collector: suspend (value: T) -> Unit) {
                        vmScope.launch { this@subscribe.collect(collector) }
                    }
                }.onSubscribed()
            }.onCompletion {
                Logger.d(TAG) { "Unsubscribed" }
                internalActionsChannel.cancel()
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
        get() = internalStateFlow.value

    private val internalEffectsChannel: Channel<E> = Channel(capacity = Channel.BUFFERED)
    val effectsFlow: Flow<E> = internalEffectsChannel.receiveAsFlow()

    protected interface SubscriptionScope {
        /**
         * Subscribe this [Flow], collecting its values in [collector].
         * Automatically cancels the subscription when the [AppViewModel] is unsubscribed.
         */
        fun <T> Flow<T>.subscribe(collector: suspend (value: T) -> Unit)
    }

    protected open fun SubscriptionScope.onSubscribed() {
        // Nothing gets subscribed
    }

    protected open fun onUnsubscribed() {
        // Nothing gets unsubscribed
    }

    protected open fun onAction(action: A) {
        Logger.w(TAG) { "Received unhandled action: $action" }
    }

    fun send(action: A) {
        Logger.d(TAG) { "Send action: $action" }
        internalActionsChannel
            .trySend(action)
            .onFailure { ex ->
                Logger.e(TAG, ex) { "Unable to send action: $action (${ex?.message ?: ex?.let { it::class.simpleName }})" }
            }
    }

    protected fun S.update() {
        check(this !is Unit) { "Cannot update a Unit VM state" }
        Logger.d(TAG) { "Updated state: $this" }
        internalStateFlow.update { this }
    }

    protected fun E.sendEffect() {
        check(this !is Unit) { "Cannot send a Unit VM effect" }
        Logger.d(TAG) { "Send effect: $this" }
        vmScope.launch {
            internalEffectsChannel.send(this@sendEffect)
        }
    }
}
