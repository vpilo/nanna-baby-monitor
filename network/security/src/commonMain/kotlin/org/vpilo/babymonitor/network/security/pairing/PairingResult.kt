package org.vpilo.babymonitor.network.security.pairing

sealed interface PairingResult {
    data class Success(
        val serverConfirmation: ByteArray,
    ) : PairingResult {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Success

            return serverConfirmation.contentEquals(other.serverConfirmation)
        }

        override fun hashCode(): Int = serverConfirmation.contentHashCode()
    }

    data class Failure(
        val reason: String,
    ) : PairingResult
}
