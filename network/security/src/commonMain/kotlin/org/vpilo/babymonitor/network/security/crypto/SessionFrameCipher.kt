package org.vpilo.babymonitor.network.security.crypto

import org.vpilo.babymonitor.network.security.crypto.internal.AES_GCM_NONCE_SIZE_BYTES
import org.vpilo.babymonitor.network.security.crypto.internal.openAes256Gcm
import org.vpilo.babymonitor.network.security.crypto.internal.sealAes256Gcm

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
