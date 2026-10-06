package org.vpilo.babymonitor.network.model.pairing

import org.vpilo.babymonitor.model.Device

/**
 * Client side pairing repository.
 */
interface ClientPairingRepository {
    /**
     * Attempt to pair with the given [server] using [clientDevice] and [pin].
     * The optional [serverFingerprint] is the server's fingerprint from a scanned QR code; may be `null` for a PIN entered manually.
     */
    suspend fun pairWith(
        server: Device.Server,
        clientDevice: Device.Client,
        pin: Pin,
        serverFingerprint: String? = null,
    ): ClientPairingState
}
