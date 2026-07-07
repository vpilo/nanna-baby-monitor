package org.vpilo.babymonitor.app.clientpairing

sealed interface ClientPairingScreenEffect {
    data object Paired : ClientPairingScreenEffect
}
