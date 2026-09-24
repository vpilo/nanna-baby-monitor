package org.vpilo.babymonitor.network.security.crypto

import kotlinx.coroutines.test.runTest
import org.vpilo.babymonitor.network.model.pairing.Pin
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PairingHandshakeTest {
    @Test
    fun bothSidesAgreeOnSWhenPinMatches() =
        runTest {
            val client = EcdhKeyPair.create()
            val server = EcdhKeyPair.create()
            val pin = Pin.generate()

            val clientSecret = deriveSharedSecretS(client.deriveSharedSecret(server.publicKeyEncoded))
            val serverSecret = deriveSharedSecretS(server.deriveSharedSecret(client.publicKeyEncoded))
            assertEquals(clientSecret.toList(), serverSecret.toList())

            val transcript =
                buildPairingTranscript(client.publicKeyEncoded, server.publicKeyEncoded, SERVER_FINGERPRINT, CLIENT_FINGERPRINT)

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
            val transcript =
                buildPairingTranscript(client.publicKeyEncoded, server.publicKeyEncoded, SERVER_FINGERPRINT, CLIENT_FINGERPRINT)

            val mc = computeClientConfirmation(checkNotNull(Pin.fromStringOrNull("AB23CD")), transcript)

            assertFalse(verifyClientConfirmation(checkNotNull(Pin.fromStringOrNull("EF45GH")), transcript, mc))
        }

    @Test
    fun confirmationFailsWhenCertFingerprintDiffers_simulatingMitm() =
        runTest {
            val client = EcdhKeyPair.create()
            val server = EcdhKeyPair.create()
            val pin = Pin.generate()

            val transcriptSeenByClient =
                buildPairingTranscript(client.publicKeyEncoded, server.publicKeyEncoded, SERVER_FINGERPRINT, CLIENT_FINGERPRINT)
            val transcriptSeenByServer =
                buildPairingTranscript(client.publicKeyEncoded, server.publicKeyEncoded, MITM_FINGERPRINT, CLIENT_FINGERPRINT)

            val ms = computeServerConfirmation(pin, transcriptSeenByServer)

            assertFalse(verifyServerConfirmation(pin, transcriptSeenByClient, ms))
        }

    @Test
    fun transcriptIncludesTheClientFingerprint() {
        val client = ByteArray(65) { 1 }
        val server = ByteArray(65) { 2 }

        val transcript = buildPairingTranscript(client, server, SERVER_FINGERPRINT, CLIENT_FINGERPRINT)

        assertContentEquals(
            client + server + SERVER_FINGERPRINT.encodeToByteArray() + CLIENT_FINGERPRINT.encodeToByteArray(),
            transcript,
        )
    }

    @Test
    fun tamperedClientFingerprintFailsBothConfirmations() =
        runTest {
            val client = EcdhKeyPair.create()
            val server = EcdhKeyPair.create()
            val pin = Pin.generate()

            // A MITM swaps the fingerprint in the hello: the camera sees MITM_FINGERPRINT, the monitor its own.
            val transcriptSeenByClient =
                buildPairingTranscript(client.publicKeyEncoded, server.publicKeyEncoded, SERVER_FINGERPRINT, CLIENT_FINGERPRINT)
            val transcriptSeenByServer =
                buildPairingTranscript(client.publicKeyEncoded, server.publicKeyEncoded, SERVER_FINGERPRINT, MITM_FINGERPRINT)

            val mc = computeClientConfirmation(pin, transcriptSeenByClient)
            assertFalse(verifyClientConfirmation(pin, transcriptSeenByServer, mc))

            val ms = computeServerConfirmation(pin, transcriptSeenByServer)
            assertFalse(verifyServerConfirmation(pin, transcriptSeenByClient, ms))
        }

    private companion object {
        private val SERVER_FINGERPRINT = "a1".repeat(32)
        private val CLIENT_FINGERPRINT = "b2".repeat(32)
        private val MITM_FINGERPRINT = "c3".repeat(32)
    }
}
