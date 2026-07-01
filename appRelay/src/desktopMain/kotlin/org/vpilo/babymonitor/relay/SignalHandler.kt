package org.vpilo.babymonitor.relay

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.runBlocking
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.common.ktx.prettify
import sun.misc.Signal
import sun.misc.SignalHandler as JavaSignalHandler

object SignalHandler {
    private val TAG = SignalHandler::class

    private val signals = Channel<String>(1)

    private val handler: JavaSignalHandler = { signal ->
        signals
            .trySend(signal.name)
            .onFailure {
                Logger.e(TAG, it) { "Already handling a signal, ignored ${signal.name}. ${it?.prettify() ?: ""}" }
            }
    }

    fun awaitSignal(doOnSignal: (String) -> Unit) {
        listOf("INT", "TERM").forEach { signalName ->
            Signal.handle(Signal(signalName), handler)
        }
        runBlocking {
            val signal = signals.receive()
            doOnSignal(signal)
        }
    }
}
