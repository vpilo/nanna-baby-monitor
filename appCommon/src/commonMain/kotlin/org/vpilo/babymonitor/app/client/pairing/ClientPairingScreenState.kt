package org.vpilo.babymonitor.app.client.pairing

import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.model.repository.PairingState

data class ClientPairingScreenState(
    val server: Device.Server? = null,
    val pairingState: PairingState = PairingState.Waiting,
)
