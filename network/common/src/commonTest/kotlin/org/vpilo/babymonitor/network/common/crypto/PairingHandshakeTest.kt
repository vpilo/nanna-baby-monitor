package org.vpilo.babymonitor.network.common.crypto

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PairingHandshakeTest {
    @Test
    fun bothSidesAgreeOnSWhenPinMatches() =
        runTest {
            val client = EcdhKeyPair.create()
            val server = EcdhKeyPair.create()
            val pin = "AB23CD"
            val fingerprint = "deadbeef".repeat(8)

            val clientSecret = deriveSharedSecretS(client.deriveSharedSecret(server.publicKeyEncoded))
            val serverSecret = deriveSharedSecretS(server.deriveSharedSecret(client.publicKeyEncoded))
            assertEquals(clientSecret.toList(), serverSecret.toList())

            val transcript = buildPairingTranscript(client.publicKeyEncoded, server.publicKeyEncoded, fingerprint)

            val mc = computeClientConfirmation(pin, transcript)
            assertTrue(verifyClientConfirmation(pin, transcript, mc))

            val ms = computeServerConfirmation(pin, transcript)
            assertTrue(verifyServerConfirmation(pin, transcript, ms))
        }

    @Test
    fun confirmationFailsWithWrongPin() =
        runTest {
            val client = EcdhKeyPair.create()
            val server = EcdhKeyPair.create()
            val transcript = buildPairingTranscript(client.publicKeyEncoded, server.publicKeyEncoded, "fingerprint")

            val mc = computeClientConfirmation("AB23CD", transcript)

            assertFalse(verifyClientConfirmation("WRONG1", transcript, mc))
        }

    @Test
    fun confirmationFailsWhenCertFingerprintDiffers_simulatingMitm() =
        runTest {
            val client = EcdhKeyPair.create()
            val server = EcdhKeyPair.create()
            val pin = "AB23CD"

            val transcriptSeenByClient = buildPairingTranscript(client.publicKeyEncoded, server.publicKeyEncoded, "real-fingerprint")
            val transcriptSeenByServer = buildPairingTranscript(client.publicKeyEncoded, server.publicKeyEncoded, "mitm-fingerprint")

            val ms = computeServerConfirmation(pin, transcriptSeenByServer)

            assertFalse(verifyServerConfirmation(pin, transcriptSeenByClient, ms))
        }
}
