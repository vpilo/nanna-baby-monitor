package org.vpilo.babymonitor.network.relay

data class RelayConfig(
    val port: Int,
    val secret: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RelayConfig) return false
        return port == other.port && secret.contentEquals(other.secret)
    }

    override fun hashCode(): Int = 31 * port + secret.contentHashCode()
}
