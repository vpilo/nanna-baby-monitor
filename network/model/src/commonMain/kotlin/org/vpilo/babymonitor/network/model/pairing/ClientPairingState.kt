package org.vpilo.babymonitor.network.model.pairing

sealed interface ClientPairingState {
    data object Waiting : ClientPairingState

    data object InProgress : ClientPairingState

    data object Success : ClientPairingState

    data class Failure(
        val reason: ClientPairingFailureCause,
    ) : ClientPairingState
}
