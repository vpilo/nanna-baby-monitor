package org.vpilo.babymonitor.app.client.pairing

sealed interface ClientPairingScreenAction {
    data class SubmitPin(
        val pin: String,
    ) : ClientPairingScreenAction

    data class SubmitQr(
        val qr: String,
    ) : ClientPairingScreenAction
}
