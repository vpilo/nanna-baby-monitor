package org.vpilo.babymonitor.app.clientpairing

sealed interface ClientPairingScreenAction {
    data class SubmitPin(
        val pin: String,
    ) : ClientPairingScreenAction
}
