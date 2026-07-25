package org.vpilo.babymonitor.network.server.identity

import java.security.KeyStore

/**
 * Key store configuration required by the server engine to install TLS.
 *
 * Only one password is used for both key store and private key for simplicity.
 */
internal data class KeyStoreConfig(
    val keyStore: KeyStore,
    val keyAlias: String,
    val password: CharArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as KeyStoreConfig

        if (keyStore != other.keyStore) return false
        if (keyAlias != other.keyAlias) return false
        if (!password.contentEquals(other.password)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = keyStore.hashCode()
        result = 31 * result + keyAlias.hashCode()
        result = 31 * result + password.contentHashCode()
        return result
    }
}
