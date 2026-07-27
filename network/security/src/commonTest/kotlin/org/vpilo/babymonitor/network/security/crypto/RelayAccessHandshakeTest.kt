package org.vpilo.babymonitor.network.security.crypto

import kotlinx.coroutines.test.runTest
import org.vpilo.babymonitor.model.AppRole
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val ENDPOINT = "/relay/client/video"
private const val FINGERPRINT = "aa11bb22cc33dd44ee55ff6600778899aa11bb22cc33dd44ee55ff6600778899"

class RelayAccessHandshakeTest {
    @Test
    fun bothSidesProveThemselvesToEachOther() =
        runTest {
            val accessKey = Random.nextBytes(32)
            val connectorNonce = generateRelayNonce()
            val relayNonce = generateRelayNonce()

            val connectorProof =
                computeConnectorAccessProof(accessKey, AppRole.CLIENT, ENDPOINT, FINGERPRINT, connectorNonce, relayNonce)
            assertTrue(
                verifyConnectorAccessProof(
                    accessKey,
                    AppRole.CLIENT,
                    ENDPOINT,
                    FINGERPRINT,
                    connectorNonce,
                    relayNonce,
                    connectorProof,
                ),
            )

            val relayProof =
                computeRelayAccessProof(accessKey, AppRole.CLIENT, ENDPOINT, FINGERPRINT, connectorNonce, relayNonce)
            assertTrue(
                verifyRelayAccessProof(accessKey, AppRole.CLIENT, ENDPOINT, FINGERPRINT, connectorNonce, relayNonce, relayProof),
            )
        }

    @Test
    fun aConnectorProofIsNotAlsoARelayProof() =
        runTest {
            val accessKey = Random.nextBytes(32)
            val connectorNonce = generateRelayNonce()
            val relayNonce = generateRelayNonce()

            val connectorProof =
                computeConnectorAccessProof(accessKey, AppRole.SERVER, ENDPOINT, FINGERPRINT, connectorNonce, relayNonce)

            assertFalse(
                verifyRelayAccessProof(
                    accessKey,
                    AppRole.SERVER,
                    ENDPOINT,
                    FINGERPRINT,
                    connectorNonce,
                    relayNonce,
                    connectorProof,
                ),
            )
        }

    @Test
    fun proofsFailUnderTheWrongPassphrase() =
        runTest {
            val connectorNonce = generateRelayNonce()
            val relayNonce = generateRelayNonce()
            val proof =
                computeConnectorAccessProof(
                    Random.nextBytes(32),
                    AppRole.CLIENT,
                    ENDPOINT,
                    FINGERPRINT,
                    connectorNonce,
                    relayNonce,
                )

            assertFalse(
                verifyConnectorAccessProof(
                    Random.nextBytes(32),
                    AppRole.CLIENT,
                    ENDPOINT,
                    FINGERPRINT,
                    connectorNonce,
                    relayNonce,
                    proof,
                ),
            )
        }

    /** The channel binding: an interceptor terminating TLS presents its own certificate, so its transcript differs. */
    @Test
    fun proofsFailWhenTheCertificateIsNotTheOneTheRelayServed() =
        runTest {
            val accessKey = Random.nextBytes(32)
            val connectorNonce = generateRelayNonce()
            val relayNonce = generateRelayNonce()
            val interceptorFingerprint = FINGERPRINT.replaceFirst("aa", "bb")

            val proof =
                computeConnectorAccessProof(
                    accessKey,
                    AppRole.CLIENT,
                    ENDPOINT,
                    interceptorFingerprint,
                    connectorNonce,
                    relayNonce,
                )

            assertFalse(
                verifyConnectorAccessProof(
                    accessKey,
                    AppRole.CLIENT,
                    ENDPOINT,
                    FINGERPRINT,
                    connectorNonce,
                    relayNonce,
                    proof,
                ),
            )
        }

    @Test
    fun aProofForOneEndpointDoesNotOpenAnother() =
        runTest {
            val accessKey = Random.nextBytes(32)
            val connectorNonce = generateRelayNonce()
            val relayNonce = generateRelayNonce()

            val proof =
                computeConnectorAccessProof(
                    accessKey,
                    AppRole.CLIENT,
                    "/relay/discovery",
                    FINGERPRINT,
                    connectorNonce,
                    relayNonce,
                )

            assertFalse(
                verifyConnectorAccessProof(accessKey, AppRole.CLIENT, ENDPOINT, FINGERPRINT, connectorNonce, relayNonce, proof),
            )
        }

    @Test
    fun aProofForOneRoleDoesNotOpenTheOther() =
        runTest {
            val accessKey = Random.nextBytes(32)
            val connectorNonce = generateRelayNonce()
            val relayNonce = generateRelayNonce()

            val proof =
                computeConnectorAccessProof(accessKey, AppRole.CLIENT, ENDPOINT, FINGERPRINT, connectorNonce, relayNonce)

            assertFalse(
                verifyConnectorAccessProof(accessKey, AppRole.SERVER, ENDPOINT, FINGERPRINT, connectorNonce, relayNonce, proof),
            )
        }

    @Test
    fun aProofDoesNotReplayOntoAFreshChallenge() =
        runTest {
            val accessKey = Random.nextBytes(32)
            val connectorNonce = generateRelayNonce()

            val proof =
                computeConnectorAccessProof(
                    accessKey,
                    AppRole.CLIENT,
                    ENDPOINT,
                    FINGERPRINT,
                    connectorNonce,
                    generateRelayNonce(),
                )

            assertFalse(
                verifyConnectorAccessProof(
                    accessKey,
                    AppRole.CLIENT,
                    ENDPOINT,
                    FINGERPRINT,
                    connectorNonce,
                    generateRelayNonce(),
                    proof,
                ),
            )
        }

    @Test
    fun theSamePassphraseAlwaysDerivesTheSameKeyAndDifferentOnesDoNot() =
        runTest {
            assertContentEquals(deriveRelayAccessKey("correct horse"), deriveRelayAccessKey("correct horse"))
            assertFalse(deriveRelayAccessKey("correct horse").contentEquals(deriveRelayAccessKey("correct horsf")))
        }
}
