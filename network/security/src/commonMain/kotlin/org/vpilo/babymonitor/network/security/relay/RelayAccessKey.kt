package org.vpilo.babymonitor.network.security.relay

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.vpilo.babymonitor.network.security.crypto.deriveRelayAccessKey

/**
 * Caches the derived relay access key for the passphrase currently in use.
 *
 * Derivation is deliberately expensive, and every relay connection - including each reconnection attempt of the
 * retry loops - needs the key, so deriving per connection would make reconnecting cost seconds.
 */
internal object RelayAccessKey {
    private val mutex = Mutex()
    private var passphrase: String? = null
    private var accessKey: ByteArray? = null

    suspend fun forPassphrase(passphrase: String): ByteArray =
        mutex.withLock {
            accessKey?.takeIf { this.passphrase == passphrase }
                ?: deriveRelayAccessKey(passphrase).also {
                    this.passphrase = passphrase
                    accessKey = it
                }
        }
}
