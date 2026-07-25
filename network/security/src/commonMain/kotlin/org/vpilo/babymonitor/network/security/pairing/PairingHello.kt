package org.vpilo.babymonitor.network.security.pairing

import org.vpilo.babymonitor.model.repository.DeviceId

data class PairingHello(
    val clientId: DeviceId,
    val clientName: String,
    val publicKey: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PairingHello

        if (clientId != other.clientId) return false
        if (clientName != other.clientName) return false
        if (!publicKey.contentEquals(other.publicKey)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = clientId.hashCode()
        result = 31 * result + clientName.hashCode()
        result = 31 * result + publicKey.contentHashCode()
        return result
    }
}
