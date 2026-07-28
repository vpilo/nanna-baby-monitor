package org.vpilo.babymonitor.network.model.pairing

sealed interface ClientPairingState {
    data object Idle : ClientPairingState

    data object InProgress : ClientPairingState

    data class Success(
        val paired: PairedServer,
    ) : ClientPairingState

    data class Failure(
        val reason: ClientPairingFailureCause,
    ) : ClientPairingState
}
