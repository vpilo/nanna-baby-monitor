package org.vpilo.babymonitor.network.common.crypto

import java.security.SecureRandom

const val SESSION_SALT_SIZE_BYTES = 32

private val secureRandom = SecureRandom()
private val CLIENT_PROOF_LABEL = "c".encodeToByteArray()
private val SERVER_PROOF_LABEL = "s".encodeToByteArray()
private val CLIENT_TO_SERVER_INFO = "c2s".encodeToByteArray()
private val SERVER_TO_CLIENT_INFO = "s2c".encodeToByteArray()
private const val SESSION_KEY_SIZE_BYTES = 32

fun generateSessionSalt(): ByteArray = ByteArray(SESSION_SALT_SIZE_BYTES).also(secureRandom::nextBytes)

data class SessionKeys(
    val clientToServer: ByteArray,
    val serverToClient: ByteArray,
)

suspend fun computeClientHandshakeProof(
    sharedSecret: ByteArray,
    clientSalt: ByteArray,
): ByteArray = hmacSha256(sharedSecret, CLIENT_PROOF_LABEL + clientSalt)

suspend fun verifyClientHandshakeProof(
    sharedSecret: ByteArray,
    clientSalt: ByteArray,
    proof: ByteArray,
): Boolean = verifyHmacSha256(sharedSecret, CLIENT_PROOF_LABEL + clientSalt, proof)

suspend fun computeServerHandshakeProof(
    sharedSecret: ByteArray,
    clientSalt: ByteArray,
    serverSalt: ByteArray,
): ByteArray = hmacSha256(sharedSecret, SERVER_PROOF_LABEL + clientSalt + serverSalt)

suspend fun verifyServerHandshakeProof(
    sharedSecret: ByteArray,
    clientSalt: ByteArray,
    serverSalt: ByteArray,
    proof: ByteArray,
): Boolean = verifyHmacSha256(sharedSecret, SERVER_PROOF_LABEL + clientSalt + serverSalt, proof)

suspend fun deriveSessionKeys(
    sharedSecret: ByteArray,
    clientSalt: ByteArray,
    serverSalt: ByteArray,
): SessionKeys {
    val combinedSalt = clientSalt + serverSalt
    return SessionKeys(
        clientToServer = hkdfSha256(sharedSecret, combinedSalt, CLIENT_TO_SERVER_INFO, SESSION_KEY_SIZE_BYTES),
        serverToClient = hkdfSha256(sharedSecret, combinedSalt, SERVER_TO_CLIENT_INFO, SESSION_KEY_SIZE_BYTES),
    )
}

fun SessionKeys.asServerCipher(associatedData: ByteArray): SessionFrameCipher =
    SessionFrameCipher(sendKey = serverToClient, receiveKey = clientToServer, associatedData = associatedData)

fun SessionKeys.asClientCipher(associatedData: ByteArray): SessionFrameCipher =
    SessionFrameCipher(sendKey = clientToServer, receiveKey = serverToClient, associatedData = associatedData)

/**
 * Seals/opens frames for one direction pair of a single connection, using a monotonic counter as the
 * AES-GCM nonce — safe only because [sendKey]/[receiveKey] are fresh per session (see the session handshake
 * above), so counter-from-0 never repeats a (key, nonce) pair. Wire format: `[8-byte big-endian counter][ciphertext + 16-byte tag]`.
 */
class SessionFrameCipher internal constructor(
    private val sendKey: ByteArray,
    private val receiveKey: ByteArray,
    private val associatedData: ByteArray,
) {
    private var sendCounter: Long = 0

    suspend fun seal(plaintext: ByteArray): ByteArray {
        val counter = sendCounter++
        val sealed = sealAes256Gcm(sendKey, counter.toNonce(), plaintext, associatedData)
        return counter.toCounterBytes() + sealed
    }

    /** Trusts the counter embedded in [wireFrame] to reconstruct the nonce — safe because the WebSocket
     * transport already guarantees in-order, non-duplicated delivery within a connection, so there's no
     * replay to defend against here beyond what AES-GCM's tag already catches (tampering). */
    suspend fun open(wireFrame: ByteArray): ByteArray {
        require(wireFrame.size > COUNTER_SIZE_BYTES) { "Frame too short" }
        val counter = wireFrame.copyOfRange(0, COUNTER_SIZE_BYTES).toLongCounter()
        val ciphertext = wireFrame.copyOfRange(COUNTER_SIZE_BYTES, wireFrame.size)
        return openAes256Gcm(receiveKey, counter.toNonce(), ciphertext, associatedData)
    }

    private companion object {
        private const val COUNTER_SIZE_BYTES = 8

        private fun Long.toCounterBytes(): ByteArray =
            ByteArray(COUNTER_SIZE_BYTES) { i -> (this ushr ((COUNTER_SIZE_BYTES - 1 - i) * 8)).toByte() }

        private fun ByteArray.toLongCounter(): Long = fold(0L) { acc, byte -> (acc shl 8) or (byte.toLong() and 0xFF) }

        // 12-byte AES-GCM nonce = 4 zero bytes + 8-byte counter.
        private fun Long.toNonce(): ByteArray = ByteArray(AES_GCM_NONCE_SIZE_BYTES - COUNTER_SIZE_BYTES) + toCounterBytes()
    }
}
