package org.vpilo.babymonitor.network.server.identity

import io.ktor.network.tls.certificates.buildKeyStore
import io.ktor.network.tls.certificates.saveToFile
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.network.common.Constants
import org.vpilo.babymonitor.network.security.crypto.sha256Fingerprint
import org.vpilo.babymonitor.settings.model.getSettingsDir
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import kotlin.io.encoding.Base64

/**
 * The server's persistent, per-install self-signed TLS identity. Loaded (or generated on first launch)
 * once at startup and reused for the lifetime of the process — this is the pinning anchor clients trust
 * after pairing (see [sha256Fingerprint]).
 */
internal class ServerIdentity private constructor(
    private val keyStore: KeyStore,
    private val password: String,
) {
    private val certificate: X509Certificate
        get() = keyStore.getCertificate(KEY_ALIAS) as X509Certificate

    val fingerprint: String
        get() = certificate.sha256Fingerprint()

    fun toKeyStoreConfig(): KeyStoreConfig =
        KeyStoreConfig(
            keyStore = keyStore,
            keyAlias = KEY_ALIAS,
            password = password.toCharArray(),
        )

    companion object {
        fun loadOrCreate(): ServerIdentity {
            val directory = File(getSettingsDir(), IDENTITY_DIRECTORY_NAME)
            val keyStoreFile = File(directory, KEYSTORE_FILE_NAME)
            val password = loadOrCreatePassword(directory)

            if (keyStoreFile.exists()) {
                // buildKeyStore() below always creates KeyStore.getDefaultType(); Android has no "JKS" provider,
                // unlike desktop JVMs, so the type used for loading must match that rather than being hardcoded.
                val keyStore =
                    keyStoreFile.inputStream().use { input ->
                        KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(input, password.toCharArray()) }
                    }
                Logger.i(TAG) { "Loaded server identity" }
                return ServerIdentity(keyStore, password)
            }

            check(directory.exists() || directory.mkdirs()) { "Failed to create ${directory.path}" }
            val keyStore =
                buildKeyStore {
                    certificate(KEY_ALIAS) {
                        this.password = password
                        domains = listOf(Constants.TLS_SERVER_NAME, Constants.SERVICES_LISTEN_ADDRESS)
                        keySizeInBits = 2048
                        daysValid = CERTIFICATE_VALIDITY_DAYS
                    }
                }
            keyStore.saveToFile(keyStoreFile, password)
            Logger.i(TAG) { "Generated server identity" }
            return ServerIdentity(keyStore, password)
        }

        private fun loadOrCreatePassword(directory: File): String {
            val passwordFile = File(directory, PASSWORD_FILE_NAME)
            if (passwordFile.exists()) return passwordFile.readText()

            check(directory.exists() || directory.mkdirs()) { "Failed to create ${directory.path}" }
            val bytes = ByteArray(PASSWORD_LENGTH_BYTES)
            SecureRandom().nextBytes(bytes)
            val password = Base64.encode(bytes)
            passwordFile.writeText(password)
            return password
        }

        private const val IDENTITY_DIRECTORY_NAME = "identity"
        private const val KEYSTORE_FILE_NAME = "server_keystore"
        private const val PASSWORD_FILE_NAME = "server_keystore.password"
        private const val KEY_ALIAS = "babymonitor-server"
        private const val CERTIFICATE_VALIDITY_DAYS = 36_500L
        private const val PASSWORD_LENGTH_BYTES = 16

        private val TAG = ServerIdentity::class
    }
}
