package org.vpilo.babymonitor.app.server.pairing

import org.vpilo.babymonitor.model.repository.PairingWindowState

data class ServerPairingScreenState(
    val pairingState: PairingWindowState = PairingWindowState.Idle,
)
