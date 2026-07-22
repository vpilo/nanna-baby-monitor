package org.vpilo.babymonitor.network.common.crypto.internal

import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class AeadTest {
    @Test
    fun sealThenOpenRoundTrips() =
        runTest {
            val key = Random.nextBytes(AES_256_GCM_KEY_SIZE_BYTES)
            val nonce = Random.nextBytes(AES_GCM_NONCE_SIZE_BYTES)
            val plaintext = "hello baby monitor".encodeToByteArray()
            val aad = "device-42".encodeToByteArray()

            val sealed = sealAes256Gcm(key, nonce, plaintext, aad)
            val opened = openAes256Gcm(key, nonce, sealed, aad)

            assertContentEquals(plaintext, opened)
            assertFalse(sealed.toList().containsAll(plaintext.toList()) && plaintext.isNotEmpty().not())
        }

    @Test
    fun tamperedCiphertextFailsToOpen() =
        runTest {
            val key = Random.nextBytes(AES_256_GCM_KEY_SIZE_BYTES)
            val nonce = Random.nextBytes(AES_GCM_NONCE_SIZE_BYTES)
            val aad = "device-42".encodeToByteArray()
            val sealed = sealAes256Gcm(key, nonce, "payload".encodeToByteArray(), aad)
            sealed[0] = sealed[0].inc()

            assertFailsWith<Throwable> {
                openAes256Gcm(key, nonce, sealed, aad)
            }
        }

    @Test
    fun mismatchedAssociatedDataFailsToOpen() =
        runTest {
            val key = Random.nextBytes(AES_256_GCM_KEY_SIZE_BYTES)
            val nonce = Random.nextBytes(AES_GCM_NONCE_SIZE_BYTES)
            val sealed =
                sealAes256Gcm(
                    key,
                    nonce,
                    "payload".encodeToByteArray(),
                    "aad-a".encodeToByteArray(),
                )

            assertFailsWith<Throwable> {
                openAes256Gcm(key, nonce, sealed, "aad-b".encodeToByteArray())
            }
        }
}
