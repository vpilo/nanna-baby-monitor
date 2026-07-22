package org.vpilo.babymonitor.network.common.crypto

data class SessionKeys(
    val clientToServer: ByteArray,
    val serverToClient: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SessionKeys

        if (!clientToServer.contentEquals(other.clientToServer)) return false
        if (!serverToClient.contentEquals(other.serverToClient)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = clientToServer.contentHashCode()
        result = 31 * result + serverToClient.contentHashCode()
        return result
    }
}
