package org.vpilo.babymonitor.app.server.pairing

import org.vpilo.babymonitor.network.model.pairing.ServerPairingState

data class ServerPairingScreenState(
    val serverName: String = "Server",
    val pairingState: ServerPairingState = ServerPairingState.Idle,
) {
    override fun toString(): String = "ServerPairingScreenState($pairingState)"
}
