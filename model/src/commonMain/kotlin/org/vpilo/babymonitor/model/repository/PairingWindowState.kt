package org.vpilo.babymonitor.model.repository

sealed interface PairingWindowState {
    data object Idle : PairingWindowState

    data class Active(
        val pin: String,
        val qrText: String,
    ) : PairingWindowState

    data class Succeeded(
        val clientName: String,
    ) : PairingWindowState

    data class Failed(
        val reason: PairingFailureReason,
    ) : PairingWindowState
}

enum class PairingFailureReason {
    WRONG_PIN_LOCKOUT,
    WINDOW_EXPIRED,
}
