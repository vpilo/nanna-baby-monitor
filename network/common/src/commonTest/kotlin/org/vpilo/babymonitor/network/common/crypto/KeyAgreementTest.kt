package org.vpilo.babymonitor.network.common.crypto

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse

class KeyAgreementTest {
    @Test
    fun bothSidesDeriveTheSameSharedSecret() =
        runTest {
            val alice = generateEcdhKeyPair()
            val bob = generateEcdhKeyPair()

            val aliceSecret = alice.deriveSharedSecret(bob.publicKeyEncoded)
            val bobSecret = bob.deriveSharedSecret(alice.publicKeyEncoded)

            assertContentEquals(aliceSecret, bobSecret)
        }

    @Test
    fun differentPeersDeriveDifferentSecrets() =
        runTest {
            val alice = generateEcdhKeyPair()
            val bob = generateEcdhKeyPair()
            val mallory = generateEcdhKeyPair()

            val aliceBobSecret = alice.deriveSharedSecret(bob.publicKeyEncoded)
            val aliceMallorySecret = alice.deriveSharedSecret(mallory.publicKeyEncoded)

            assertFalse(aliceBobSecret.contentEquals(aliceMallorySecret))
        }
}
