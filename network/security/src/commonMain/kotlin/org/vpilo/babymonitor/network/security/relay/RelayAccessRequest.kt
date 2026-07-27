package org.vpilo.babymonitor.network.security.relay

/**
 * The connector's nonce and its proof, sent together in reply to the relay's challenge.
 *
 * The connector always proves first, so an unauthenticated peer never obtains a relay proof to attack offline.
 */
data class RelayAccessRequest(
    val nonce: ByteArray,
    val proof: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RelayAccessRequest

        if (!nonce.contentEquals(other.nonce)) return false
        if (!proof.contentEquals(other.proof)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = nonce.contentHashCode()
        result = 31 * result + proof.contentHashCode()
        return result
    }
}
