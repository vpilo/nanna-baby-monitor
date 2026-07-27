package org.vpilo.babymonitor.network.security.crypto.internal

import dev.whyoleg.cryptography.BinarySize.Companion.bytes
import dev.whyoleg.cryptography.algorithms.PBKDF2
import dev.whyoleg.cryptography.algorithms.SHA256

/**
 * Derives [outputSizeBytes] of key material from a human-chosen [password], RFC 8018.
 *
 * Unlike [hkdfSha256] this is deliberately slow: the input has little entropy, so the only defense against an
 * offline guessing attack is making each guess expensive.
 */
internal suspend fun pbkdf2Sha256(
    password: ByteArray,
    salt: ByteArray,
    iterations: Int,
    outputSizeBytes: Int,
): ByteArray =
    cryptographyProvider
        .get(PBKDF2)
        .secretDerivation(
            digest = SHA256,
            iterations = iterations,
            outputSize = outputSizeBytes.bytes,
            salt = salt,
        ).deriveSecretToByteArray(password)
