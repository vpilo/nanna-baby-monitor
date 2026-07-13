package org.vpilo.babymonitor.app.client.pairing

import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.model.repository.PairingOutcome

data class ClientPairingScreenState(
    val server: Device.Server? = null,
    val isPairing: Boolean = false,
    val outcome: PairingOutcome? = null,
)
