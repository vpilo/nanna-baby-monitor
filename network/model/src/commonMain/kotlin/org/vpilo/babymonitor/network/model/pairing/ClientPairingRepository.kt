package org.vpilo.babymonitor.network.model.pairing

import org.vpilo.babymonitor.model.Device

interface ClientPairingRepository {
    suspend fun pairWith(
        server: Device.Server,
        clientDevice: Device.Client,
        pin: Pin,
    ): ClientPairingState
}
