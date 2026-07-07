package org.vpilo.babymonitor.model.repository

sealed interface PairingOutcome {
    data object Success : PairingOutcome

    data class Failure(
        val reason: PairingFailureCause,
    ) : PairingOutcome
}

enum class PairingFailureCause {
    WRONG_PIN,
    NO_ACTIVE_PAIRING_WINDOW,
    SERVER_NOT_ON_NETWORK,
    MITM_SUSPECTED,
    CONNECTION_FAILED,
}
