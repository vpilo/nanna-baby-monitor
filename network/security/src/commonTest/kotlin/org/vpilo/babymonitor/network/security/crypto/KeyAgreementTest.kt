package org.vpilo.babymonitor.network.security.crypto

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse

class KeyAgreementTest {
    @Test
    fun bothSidesDeriveTheSameSharedSecret() =
        runTest {
            val alice = EcdhKeyPair.create()
            val bob = EcdhKeyPair.create()

            val aliceSecret = alice.deriveSharedSecret(bob.publicKeyEncoded)
            val bobSecret = bob.deriveSharedSecret(alice.publicKeyEncoded)

            assertContentEquals(aliceSecret, bobSecret)
        }

    @Test
    fun differentPeersDeriveDifferentSecrets() =
        runTest {
            val alice = EcdhKeyPair.create()
            val bob = EcdhKeyPair.create()
            val mallory = EcdhKeyPair.create()

            val aliceBobSecret = alice.deriveSharedSecret(bob.publicKeyEncoded)
            val aliceMallorySecret = alice.deriveSharedSecret(mallory.publicKeyEncoded)

            assertFalse(aliceBobSecret.contentEquals(aliceMallorySecret))
        }
}
