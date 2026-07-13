package org.vpilo.babymonitor.network.server.identity

import org.vpilo.babymonitor.network.common.crypto.sha256Fingerprint
import java.security.KeyStore
import java.security.cert.X509Certificate

/**
 * Key store configuration required by the server engine to install TLS.
 *
 * Only one password is used for both key store and private key for simplicity.
 */
data class KeyStoreConfig(
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

/**
 * The server's persistent, per-install self-signed TLS identity. Loaded (or generated on first launch)
 * once at startup and reused for the lifetime of the process — this is the pinning anchor clients trust
 * after pairing (see [sha256Fingerprint]).
 */
expect class ServerIdentity {
    val certificate: X509Certificate

    fun toKeyStoreConfig(): KeyStoreConfig

    companion object {
        suspend fun loadOrCreate(): ServerIdentity
    }
}

val ServerIdentity.fingerprint: String
    get() = certificate.sha256Fingerprint()
