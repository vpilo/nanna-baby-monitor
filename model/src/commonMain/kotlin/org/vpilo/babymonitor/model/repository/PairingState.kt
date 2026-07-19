package org.vpilo.babymonitor.model.repository

sealed interface PairingState {
    data object Waiting : PairingState

    data object InProgress : PairingState

    data object Success : PairingState

    data class Failure(
        val reason: PairingFailureCause,
    ) : PairingState
}

enum class PairingFailureCause {
    INVALID_QR,
    WRONG_PIN,
    NO_ACTIVE_PAIRING_WINDOW,
    SERVER_NOT_ON_NETWORK,
    MITM_SUSPECTED,
    CONNECTION_FAILED,
    WRONG_DEVICE,
}
