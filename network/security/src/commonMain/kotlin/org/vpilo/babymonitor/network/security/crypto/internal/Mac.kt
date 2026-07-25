package org.vpilo.babymonitor.network.security.crypto.internal

import dev.whyoleg.cryptography.algorithms.HMAC
import dev.whyoleg.cryptography.algorithms.SHA256

internal suspend fun hmacSha256(
    key: ByteArray,
    data: ByteArray,
): ByteArray {
    val hmacKey = cryptographyProvider.get(HMAC).keyDecoder(SHA256).decodeFromByteArray(HMAC.Key.Format.RAW, key)
    return hmacKey.signatureGenerator().generateSignature(data)
}

/**
 * Returns `false` on any mismatch (wrong key, tampered data, or truncated tag) instead of throwing —
 * a failed proof is an expected outcome (wrong PIN, revoked pairing), not an error.
 */
internal suspend fun verifyHmacSha256(
    key: ByteArray,
    data: ByteArray,
    expectedTag: ByteArray,
): Boolean {
    val hmacKey = cryptographyProvider.get(HMAC).keyDecoder(SHA256).decodeFromByteArray(HMAC.Key.Format.RAW, key)
    return hmacKey.signatureVerifier().tryVerifySignature(data, expectedTag)
}
