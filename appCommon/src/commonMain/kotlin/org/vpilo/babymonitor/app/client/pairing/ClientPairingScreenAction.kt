package org.vpilo.babymonitor.app.client.pairing

sealed interface ClientPairingScreenAction {
    data class SubmitPin(
        val pin: String,
        val device: String?,
    ) : ClientPairingScreenAction
}
