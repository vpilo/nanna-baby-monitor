package org.vpilo.babymonitor.network.server.identity

import io.ktor.network.tls.certificates.buildKeyStore
import io.ktor.network.tls.certificates.saveToFile
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.network.common.Constants
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import kotlin.io.encoding.Base64

actual class ServerIdentity private constructor(
    private val keyStore: KeyStore,
    private val password: String,
) {
    actual val certificate: X509Certificate = keyStore.getCertificate(KEY_ALIAS) as X509Certificate

    actual fun toKeyStoreConfig(): KeyStoreConfig =
        KeyStoreConfig(
            keyStore = keyStore,
            keyAlias = KEY_ALIAS,
            password = password.toCharArray(),
        )

    actual companion object {
        actual suspend fun loadOrCreate(): ServerIdentity {
            val directory = File(System.getProperty("user.home"), ".config/babymonitor")
            val keyStoreFile = File(directory, KEYSTORE_FILE_NAME)
            val password = loadOrCreatePassword(directory)

            if (keyStoreFile.exists()) {
                val keyStore =
                    keyStoreFile.inputStream().use { input ->
                        KeyStore.getInstance(KEYSTORE_TYPE).apply { load(input, password.toCharArray()) }
                    }
                Logger.i(TAG) { "Loaded server identity" }
                return ServerIdentity(keyStore, password)
            }

            check(directory.exists() || directory.mkdirs()) { "Failed to create ${directory.path}" }
            val keyStore =
                buildKeyStore {
                    certificate(KEY_ALIAS) {
                        this.password = password
                        domains = listOf(Constants.TLS_SERVER_NAME, "0.0.0.0")
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

        private const val KEYSTORE_FILE_NAME = "server_keystore.jks"
        private const val PASSWORD_FILE_NAME = "server_keystore.password"
        private const val KEYSTORE_TYPE = "JKS"
        private const val KEY_ALIAS = "babymonitor-server"
        private const val CERTIFICATE_VALIDITY_DAYS = 36_500L
        private const val PASSWORD_LENGTH_BYTES = 16

        private val TAG = ServerIdentity::class
    }
}
