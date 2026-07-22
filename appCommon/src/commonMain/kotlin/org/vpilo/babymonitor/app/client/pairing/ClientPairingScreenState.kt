package org.vpilo.babymonitor.app.client.pairing

import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.network.model.pairing.ClientPairingState

data class ClientPairingScreenState(
    val server: Device.Server? = null,
    val pairingState: ClientPairingState = ClientPairingState.Waiting,
)
