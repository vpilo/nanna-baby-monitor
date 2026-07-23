package org.vpilo.babymonitor.app.client.pairing

import org.vpilo.babymonitor.network.model.pairing.Pin

sealed interface ClientPairingScreenAction {
    data class SubmitPin(
        val pin: Pin,
    ) : ClientPairingScreenAction

    data class SubmitQr(
        val qr: String,
    ) : ClientPairingScreenAction
}
