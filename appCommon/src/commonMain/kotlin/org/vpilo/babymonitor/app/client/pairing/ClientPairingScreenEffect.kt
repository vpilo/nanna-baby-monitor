package org.vpilo.babymonitor.app.client.pairing

sealed interface ClientPairingScreenEffect {
    data object Paired : ClientPairingScreenEffect
}
