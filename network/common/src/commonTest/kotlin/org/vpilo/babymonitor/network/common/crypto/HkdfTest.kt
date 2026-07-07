package org.vpilo.babymonitor.network.common.crypto

import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HkdfTest {
    @Test
    fun sameInputsDeriveTheSameKey() =
        runTest {
            val ikm = Random.nextBytes(32)
            val salt = Random.nextBytes(16)
            val info = "test-info".encodeToByteArray()

            val first = hkdfSha256(ikm, salt, info, outputSizeBytes = 32)
            val second = hkdfSha256(ikm, salt, info, outputSizeBytes = 32)

            assertContentEquals(first, second)
            assertEquals(32, first.size)
        }

    @Test
    fun differentInfoDerivesDifferentKeys() =
        runTest {
            val ikm = Random.nextBytes(32)
            val salt = Random.nextBytes(16)

            val c2s = hkdfSha256(ikm, salt, "c2s".encodeToByteArray(), outputSizeBytes = 32)
            val s2c = hkdfSha256(ikm, salt, "s2c".encodeToByteArray(), outputSizeBytes = 32)

            assertFalse(c2s.contentEquals(s2c))
        }
}
