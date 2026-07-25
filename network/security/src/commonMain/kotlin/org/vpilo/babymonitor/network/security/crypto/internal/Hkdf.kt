package org.vpilo.babymonitor.network.security.crypto.internal

import dev.whyoleg.cryptography.BinarySize.Companion.bytes
import dev.whyoleg.cryptography.algorithms.HKDF
import dev.whyoleg.cryptography.algorithms.SHA256

/**
 * Derives [outputSizeBytes] of key material from [inputKeyMaterial] using HKDF-SHA256, RFC 5869.
 * [info] purpose-separates independent keys derived from the same [inputKeyMaterial] (e.g. "c2s" vs "s2c").
 */
internal suspend fun hkdfSha256(
    inputKeyMaterial: ByteArray,
    salt: ByteArray?,
    info: ByteArray,
    outputSizeBytes: Int,
): ByteArray =
    cryptographyProvider
        .get(HKDF)
        .secretDerivation(
            digest = SHA256,
            outputSize = outputSizeBytes.bytes,
            salt = salt,
            info = info,
        ).deriveSecretToByteArray(inputKeyMaterial)
