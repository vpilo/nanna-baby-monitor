package org.vpilo.babymonitor.network.server.identity

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.vpilo.babymonitor.common.Logger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import javax.security.auth.x500.X500Principal

actual class ServerIdentity private constructor(
    actual val certificate: X509Certificate,
) {
    actual fun toKeyStoreConfig(): KeyStoreConfig {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE_TYPE).apply { load(null) }
        return KeyStoreConfig(
            keyStore = keyStore,
            keyAlias = KEY_ALIAS,
            // AndroidKeyStore entries are not password-protected; Ktor's sslConnector still requires
            // password callbacks, so both are empty and ignored by the AndroidKeyStore provider.
            keyStorePassword = CharArray(0),
            privateKeyPassword = CharArray(0),
        )
    }

    actual companion object {
        actual suspend fun loadOrCreate(): ServerIdentity {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE_TYPE).apply { load(null) }
            val existing = keyStore.getCertificate(KEY_ALIAS) as? X509Certificate
            if (existing != null) {
                Logger.i(TAG) { "Loaded server identity" }
                return ServerIdentity(existing)
            }

            val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE_TYPE)
            generator.initialize(
                KeyGenParameterSpec
                    .Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setCertificateSubject(X500Principal("CN=babymonitor-server"))
                    .build(),
            )
            generator.generateKeyPair()

            val reopened = KeyStore.getInstance(ANDROID_KEYSTORE_TYPE).apply { load(null) }
            val certificate = reopened.getCertificate(KEY_ALIAS) as X509Certificate
            Logger.i(TAG) { "Generated server identity" }
            return ServerIdentity(certificate)
        }

        private const val ANDROID_KEYSTORE_TYPE = "AndroidKeyStore"
        private const val KEY_ALIAS = "babymonitor-server"

        private val TAG = ServerIdentity::class
    }
}
