package org.vpilo.babymonitor.network.security.crypto.internal

import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import org.jetbrains.annotations.VisibleForTesting

@VisibleForTesting
internal const val AES_256_GCM_KEY_SIZE_BYTES = 32
internal const val AES_GCM_NONCE_SIZE_BYTES = 12

/**
 * Seals [plaintext] with AES-256-GCM under [key], using the caller-supplied [nonce] (never randomly
 * generated here: callers derive it from a per-connection monotonic counter, see `SessionFrameCipher`).
 * Output is `ciphertext || 16-byte tag`, with no nonce prepended.
 */
@OptIn(DelicateCryptographyApi::class)
internal suspend fun sealAes256Gcm(
    key: ByteArray,
    nonce: ByteArray,
    plaintext: ByteArray,
    associatedData: ByteArray,
): ByteArray {
    require(key.size == AES_256_GCM_KEY_SIZE_BYTES) { "AES-256-GCM key must be $AES_256_GCM_KEY_SIZE_BYTES bytes" }
    require(nonce.size == AES_GCM_NONCE_SIZE_BYTES) { "AES-GCM nonce must be $AES_GCM_NONCE_SIZE_BYTES bytes" }
    val gcmKey = cryptographyProvider.get(AES.GCM).keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, key)
    return gcmKey.cipher().encryptWithIv(nonce, plaintext, associatedData)
}

/**
 * Opens a frame produced by [sealAes256Gcm]. Throws if [ciphertext]'s tag doesn't verify under [key]/[nonce]/[associatedData]
 * (tampered frame, wrong key, or mismatched associated data) - callers must treat any thrown exception as "drop the connection".
 */
@OptIn(DelicateCryptographyApi::class)
internal suspend fun openAes256Gcm(
    key: ByteArray,
    nonce: ByteArray,
    ciphertext: ByteArray,
    associatedData: ByteArray,
): ByteArray {
    require(key.size == AES_256_GCM_KEY_SIZE_BYTES) { "AES-256-GCM key must be $AES_256_GCM_KEY_SIZE_BYTES bytes" }
    require(nonce.size == AES_GCM_NONCE_SIZE_BYTES) { "AES-GCM nonce must be $AES_GCM_NONCE_SIZE_BYTES bytes" }
    val gcmKey = cryptographyProvider.get(AES.GCM).keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, key)
    return gcmKey.cipher().decryptWithIv(nonce, ciphertext, associatedData)
}
