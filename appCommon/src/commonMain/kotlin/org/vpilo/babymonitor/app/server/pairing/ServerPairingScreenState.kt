package org.vpilo.babymonitor.app.server.pairing

import org.vpilo.babymonitor.network.model.ServerPairingState

data class ServerPairingScreenState(
    val serverName: String = "Server",
    val pairingState: ServerPairingState = ServerPairingState.Idle,
)
