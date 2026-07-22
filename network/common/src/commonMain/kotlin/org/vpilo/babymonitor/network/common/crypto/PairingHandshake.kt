package org.vpilo.babymonitor.network.common.crypto

import org.vpilo.babymonitor.network.common.crypto.internal.hkdfSha256
import org.vpilo.babymonitor.network.common.crypto.internal.hmacSha256
import org.vpilo.babymonitor.network.common.crypto.internal.verifyHmacSha256
import org.vpilo.babymonitor.network.model.pairing.Pin

private const val PAIRING_SECRET_SIZE_BYTES = 32
private val CLIENT_CONFIRMATION_LABEL = "c".encodeToByteArray()
private val SERVER_CONFIRMATION_LABEL = "s".encodeToByteArray()
private val PAIRING_S_INFO = "babymonitor-pairing-s".encodeToByteArray()

/** `S = HKDF(Z)`, where `Z` is the raw ECDH shared secret. */
suspend fun deriveSharedSecretS(rawEcdhSecret: ByteArray): ByteArray =
    hkdfSha256(
        rawEcdhSecret,
        salt = null,
        info = PAIRING_S_INFO,
        outputSizeBytes = PAIRING_SECRET_SIZE_BYTES,
    )

/** `T = A ‖ B ‖ serverCertFingerprint`. */
fun buildPairingTranscript(
    clientPublicKey: ByteArray,
    serverPublicKey: ByteArray,
    serverCertFingerprint: String,
): ByteArray = clientPublicKey + serverPublicKey + serverCertFingerprint.encodeToByteArray()

/** `KDF(PIN)` — the PIN is low-entropy, so this exists purely to get a fixed-size HMAC key, not to slow down brute force. */
private suspend fun pinToMacKey(pin: Pin): ByteArray =
    hkdfSha256(
        pin.toString().encodeToByteArray(),
        salt = null,
        info = "babymonitor-pin".encodeToByteArray(),
        outputSizeBytes = 32,
    )

/** `Mc = HMAC(KDF(PIN), "c" ‖ T)`. */
suspend fun computeClientConfirmation(
    pin: Pin,
    transcript: ByteArray,
): ByteArray = hmacSha256(pinToMacKey(pin), CLIENT_CONFIRMATION_LABEL + transcript)

suspend fun verifyClientConfirmation(
    pin: Pin,
    transcript: ByteArray,
    mc: ByteArray,
): Boolean = verifyHmacSha256(pinToMacKey(pin), CLIENT_CONFIRMATION_LABEL + transcript, mc)

/** `Ms = HMAC(KDF(PIN), "s" ‖ T)`. */
suspend fun computeServerConfirmation(
    pin: Pin,
    transcript: ByteArray,
): ByteArray = hmacSha256(pinToMacKey(pin), SERVER_CONFIRMATION_LABEL + transcript)

suspend fun verifyServerConfirmation(
    pin: Pin,
    transcript: ByteArray,
    ms: ByteArray,
): Boolean = verifyHmacSha256(pinToMacKey(pin), SERVER_CONFIRMATION_LABEL + transcript, ms)
