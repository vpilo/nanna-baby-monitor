package org.vpilo.babymonitor.network.server.identity

import org.vpilo.babymonitor.network.common.crypto.sha256Fingerprint
import java.security.KeyStore
import java.security.cert.X509Certificate

/** What the server engine needs to install TLS: a [KeyStore] holding the identity, plus how to unlock it. */
class KeyStoreConfig(
    val keyStore: KeyStore,
    val keyAlias: String,
    val keyStorePassword: CharArray,
    val privateKeyPassword: CharArray,
)

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
