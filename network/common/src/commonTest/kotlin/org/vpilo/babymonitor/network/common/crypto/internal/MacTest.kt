package org.vpilo.babymonitor.network.common.crypto.internal

import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MacTest {
    @Test
    fun signatureVerifiesUnderTheSameKeyAndData() =
        runTest {
            val key = Random.nextBytes(32)
            val data = "transcript-bytes".encodeToByteArray()

            val tag = hmacSha256(key, data)

            assertTrue(verifyHmacSha256(key, data, tag))
        }

    @Test
    fun signatureFailsToVerifyUnderAWrongKey() =
        runTest {
            val data = "transcript-bytes".encodeToByteArray()
            val tag = hmacSha256(Random.nextBytes(32), data)

            assertFalse(verifyHmacSha256(Random.nextBytes(32), data, tag))
        }

    @Test
    fun signatureFailsToVerifyForTamperedData() =
        runTest {
            val key = Random.nextBytes(32)
            val tag = hmacSha256(key, "original".encodeToByteArray())

            assertFalse(verifyHmacSha256(key, "tampered".encodeToByteArray(), tag))
        }
}
