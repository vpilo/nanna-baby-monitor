package org.vpilo.babymonitor.network.common.crypto

import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDH
import org.vpilo.babymonitor.network.common.crypto.internal.cryptographyProvider

/**
 * An ephemeral ECDH P-256 key pair. [publicKeyEncoded] is safe to send over the wire (e.g. in a pairing
 * message); the private key never leaves this object.
 */
class EcdhKeyPair internal constructor(
    val publicKeyEncoded: ByteArray,
    private val privateKey: ECDH.PrivateKey,
) {
    suspend fun deriveSharedSecret(peerPublicKeyEncoded: ByteArray): ByteArray {
        val peerPublicKey =
            cryptographyProvider
                .get(ECDH)
                .publicKeyDecoder(EC.Curve.P256)
                .decodeFromByteArray(EC.PublicKey.Format.RAW.Uncompressed, peerPublicKeyEncoded)
        return privateKey.sharedSecretGenerator().generateSharedSecretToByteArray(peerPublicKey)
    }

    companion object {
        suspend fun create(): EcdhKeyPair {
            val keyPair = cryptographyProvider.get(ECDH).keyPairGenerator(EC.Curve.P256).generateKey()
            val publicKeyEncoded = keyPair.publicKey.encodeToByteArray(EC.PublicKey.Format.RAW.Uncompressed)
            return EcdhKeyPair(publicKeyEncoded, keyPair.privateKey)
        }
    }
}
