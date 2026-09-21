package org.vpilo.babymonitor.network.model.pairing

sealed interface ServerPairingState {
    data object Idle : ServerPairingState

    data class Active(
        val pin: Pin,
        val qrText: String,
    ) : ServerPairingState {
        override fun toString(): String = "ServerPairingState.Active"
    }

    data class Succeeded(
        val clientName: String,
    ) : ServerPairingState {
        override fun toString(): String = "ServerPairingState.Succeeded"
    }

    data class Failed(
        val reason: ServerPairingFailureReason,
    ) : ServerPairingState
}

enum class ServerPairingFailureReason {
    WRONG_PIN_LOCKOUT,
    WINDOW_EXPIRED,
}
