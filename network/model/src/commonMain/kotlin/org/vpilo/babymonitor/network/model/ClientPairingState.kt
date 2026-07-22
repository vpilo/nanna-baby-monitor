package org.vpilo.babymonitor.network.model

sealed interface ClientPairingState {
    data object Waiting : ClientPairingState

    data object InProgress : ClientPairingState

    data object Success : ClientPairingState

    data class Failure(
        val reason: ClientPairingFailureCause,
    ) : ClientPairingState
}

enum class ClientPairingFailureCause {
    INVALID_QR,
    WRONG_PIN,
    NO_ACTIVE_PAIRING_WINDOW,
    SERVER_NOT_ON_NETWORK,
    MITM_SUSPECTED,
    CONNECTION_FAILED,
    WRONG_DEVICE,
}
